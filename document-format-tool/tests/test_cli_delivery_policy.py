from argparse import Namespace
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

from docx import Document
from lxml import etree

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from format_cli import run_job, _format_report, main, runtime_info
from word_formatter.core.integrity import IntegrityValidationError, sha256_file
from word_formatter.models.results import ChangeRecord, ProcessResult
from word_formatter.models.rules import DocumentRules


class CliDeliveryPolicyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source, self.template = self.root / 'source.docx', self.root / 'template.docx'
        for path in (self.source, self.template):
            doc = Document()
            doc.add_heading('第一章 绪论', 1)
            doc.add_paragraph('作者正文。见第1页。')
            doc.save(path)
        self.original_hash = sha256_file(self.source)
        confirmed = self.root / 'confirmed.json'
        confirmed.write_text(json.dumps({
            'analyzedRules': DocumentRules().to_dict(),
            'templateSha256': sha256_file(self.template),
            'templateAnalysis': {'documentKind': 'specification', 'copyFrontMatter': False},
            'editableRules': {},
        }), encoding='utf-8')
        self.args = Namespace(source=str(self.source), template=str(self.template),
            output=str(self.root / 'output.docx'), result_json=str(self.root / 'result.json'),
            rules_file=str(confirmed), instructions_file=None, use_doubao=False, analyze_only=False)

    def fake_processor(self, source, rules, output, template, **kwargs):
        # Simulate a permitted refreshed field result; the detailed check must
        # honestly report a mismatch, while the finished file remains available.
        doc = Document(source)
        doc.paragraphs[1].text = '作者正文。见第2页。'
        doc.save(output)
        result = ProcessResult(source, output)
        result.records.append(ChangeRecord(2, '普通正文', '原格式', '确认格式', '已执行格式规则'))
        return result

    def test_text_difference_returns_downloadable_file_and_honest_warning(self):
        with patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assertTrue(payload['success'])
        self.assertFalse(payload['integrity']['passed'])
        self.assertEqual(payload['integrity']['mode'], 'format_first')
        self.assertTrue(payload['integrity']['basicChecksPassed'])
        self.assertTrue(payload['integrity']['deliveryAllowed'])
        self.assertIn('body_text_sha256', payload['integrity']['differences'])
        self.assertTrue(any('文字对比' in value for value in payload['warnings']))
        self.assertIn({'item': '普通正文', 'count': 1}, payload['formatReport']['applied'])
        self.assertTrue(payload['formatReport']['notApplied'])
        self.assertTrue(Path(self.args.output).is_file())
        self.assertEqual(Document(self.args.output).paragraphs[1].text, '作者正文。见第2页。')
        self.assertEqual(sha256_file(self.source), self.original_hash)

    def test_unreadable_output_is_not_published(self):
        def broken(source, rules, output, template, **kwargs):
            output.write_bytes(b'not a Word document')
            return ProcessResult(source, output)
        with patch('format_cli.DocumentProcessor.process', side_effect=broken):
            with self.assertRaises(IntegrityValidationError):
                run_job(self.args)
        self.assertFalse(Path(self.args.output).exists())
        self.assertFalse(json.loads(Path(self.args.result_json).read_text(encoding='utf-8'))['success'])

    def test_changed_source_is_still_rejected(self):
        def changing(source, rules, output, template, **kwargs):
            result = self.fake_processor(source, rules, output, template, **kwargs)
            doc = Document(source)
            doc.add_paragraph('意外改写原稿')
            doc.save(source)
            return result
        with patch('format_cli.DocumentProcessor.process', side_effect=changing):
            with self.assertRaisesRegex(IntegrityValidationError, '源文件发生变化'):
                run_job(self.args)
        self.assertFalse(Path(self.args.output).exists())

    def test_report_deduplicates_toc_passes_and_does_not_claim_skips_succeeded(self):
        result = ProcessResult(self.source, self.root / 'out.docx')
        result.records = [
            ChangeRecord(2, '目录标题', '旧', '新', '已处理'),
            ChangeRecord(2, '目录标题', '旧', '新', '二次刷新'),
            ChangeRecord(3, '复杂对象', '旧', '旧', '不支持自动处理', status='skipped'),
        ]
        report = _format_report(result, DocumentRules(), [])
        self.assertEqual(report['applied'], [{'item': '目录标题', 'count': 1}])
        self.assertIn({'item': '复杂对象', 'reason': '不支持自动处理',
            'status': 'skipped', 'paragraphIndex': 3, 'count': 1}, report['notApplied'])
        self.assertEqual(report['skippedCount'], 1)
        self.assertTrue(report['partialSuccess'])

    def assert_partial_delivery(self, payload, item):
        self.assertTrue(payload['success'])
        self.assertTrue(payload['partialSuccess'])
        self.assertGreater(payload['changedCount'], 0)
        self.assertIn('文档可下载', payload['message'])
        self.assertTrue(any(record['item'] == item and record['status'] == 'skipped'
                            for record in payload['formatReport']['notApplied']))
        self.assertEqual(Document(self.args.output).paragraphs[1].text, '作者正文。见第2页。')
        self.assertEqual(sha256_file(self.source), self.original_hash)
        saved = json.loads(Path(self.args.result_json).read_text(encoding='utf-8'))
        self.assertTrue(saved['partialSuccess'])
        self.assertIsNone(saved['error'])

    def test_structure_statistics_namespace_error_does_not_block_output(self):
        with patch('format_cli.DocumentAnalyzer.analyze', side_effect=etree.XPathEvalError('Undefined namespace prefix')), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assert_partial_delivery(payload, '论文结构统计')

    def test_audit_boundary_namespace_error_does_not_block_output(self):
        with patch('format_cli.DocumentProcessor._main_content_start', side_effect=etree.XPathEvalError('Undefined namespace prefix')), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assert_partial_delivery(payload, '正文对比范围识别')

    def test_detailed_audit_namespace_error_keeps_modified_document(self):
        with patch('format_cli.validate_preservation', side_effect=etree.XPathEvalError('Undefined namespace prefix')), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assert_partial_delivery(payload, '详细格式对比核对')
        self.assertFalse(payload['integrity']['passed'])
        self.assertEqual(payload['integrity']['mode'], 'format_first')
        self.assertTrue(payload['integrity']['verificationSkipped'])
        self.assertTrue(payload['integrity']['basicChecksPassed'])

    def test_optional_metadata_readers_cannot_block_input_or_output_basic_checks(self):
        for function in ('_preserved_text_hashes', '_relationships'):
            with self.subTest(function=function):
                self.args.output = str(self.root / f'{function}.docx')
                self.args.result_json = str(self.root / f'{function}.json')
                with patch(f'word_formatter.core.integrity.{function}', side_effect=etree.XPathEvalError('Undefined namespace prefix')), \
                     patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
                    payload = run_job(self.args)
                self.assert_partial_delivery(payload, '详细格式对比核对')
                self.assertEqual(payload['integrity']['mode'], 'format_first')

    def test_real_processor_returns_formatted_body_when_table_stage_throws_namespace_error(self):
        with patch('format_cli.DocumentProcessor._apply_tables', side_effect=etree.XPathEvalError('Undefined namespace prefix')), \
             patch('word_formatter.core.processor.WordDocumentConverter.update_fields_in_place'), \
             patch('format_cli.DoubaoRuleParser', side_effect=AssertionError('Formatting must be local')):
            payload = run_job(self.args)
        self.assertTrue(payload['success'])
        self.assertTrue(payload['partialSuccess'])
        self.assertGreater(payload['changedCount'], 0)
        self.assertTrue(any(item['item'] == '表格' and item['status'] == 'skipped'
                            for item in payload['formatReport']['notApplied']))
        doc = Document(self.args.output)
        body = next(p for p in doc.paragraphs if p.text.startswith('作者正文'))
        self.assertTrue(all(run.font.size.pt == 12 for run in body.runs if run.text))
        self.assertEqual(sha256_file(self.source), self.original_hash)
        saved = json.loads(Path(self.args.result_json).read_text(encoding='utf-8'))
        self.assertTrue(saved['success'])
        self.assertIn('文档可下载', saved['message'])

    def test_log_write_error_does_not_revoke_modified_document(self):
        with patch.object(ProcessResult, 'save_log', side_effect=PermissionError('log denied')), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assert_partial_delivery(payload, '处理日志写入')

    def test_log_publish_error_does_not_revoke_modified_document(self):
        from format_cli import _publish_without_overwrite

        def publish(staging, output):
            if output.name.endswith('.log.json'):
                raise RuntimeError('Cannot publish log')
            return _publish_without_overwrite(staging, output)

        with patch('format_cli._publish_without_overwrite', side_effect=publish), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assert_partial_delivery(payload, '处理日志发布')

    def test_missing_optional_roles_alone_are_not_partial_failure(self):
        with patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            payload = run_job(self.args)
        self.assertFalse(payload['partialSuccess'])
        self.assertEqual(payload['formatReport']['skippedCount'], 0)
        self.assertTrue(all(item['status'] == 'not_found' for item in payload['formatReport']['notApplied']))

    def test_zero_changes_with_skips_does_not_claim_formatting_succeeded(self):
        def skipped(source, rules, output, template, **kwargs):
            Document(source).save(output)
            result = ProcessResult(source, output)
            result.records.append(ChangeRecord(None, '自动排版', '', '', '已保留原内容', status='skipped'))
            return result
        with patch('format_cli.DocumentProcessor.process', side_effect=skipped):
            payload = run_job(self.args)
        self.assertTrue(payload['success'])
        self.assertTrue(payload['partialSuccess'])
        self.assertEqual(payload['changedCount'], 0)
        self.assertIn('未能自动完成', payload['message'])
        self.assertTrue(Path(self.args.output).is_file())

    def test_mutation_during_optional_audit_is_still_rejected(self):
        def audit(*args, **kwargs):
            Document().save(self.source)
            raise etree.XPathEvalError('Undefined namespace prefix')
        with patch('format_cli.validate_preservation', side_effect=audit), \
             patch('format_cli.DocumentProcessor.process', side_effect=self.fake_processor):
            with self.assertRaisesRegex(IntegrityValidationError, '源文件发生变化'):
                run_job(self.args)
        self.assertFalse(Path(self.args.output).exists())

    def test_runtime_info_identifies_loaded_code_without_paths_or_credentials(self):
        metadata = runtime_info()
        self.assertEqual(metadata['engineVersion'], '0.5.0')
        self.assertEqual(metadata['executionMode'], 'best_effort_local')
        self.assertEqual(metadata['type'], 'runtime')
        for field in ('workerSha256', 'processorSha256', 'namespaceHelperSha256'):
            self.assertRegex(metadata[field], r'^[a-f0-9]{64}$')
        self.assertNotIn(str(self.root), json.dumps(metadata))
        with patch.dict('os.environ', {'ARK_API_KEY': 'private-test-sentinel'}):
            with redirect_stdout(io.StringIO()) as stdout:
                self.assertEqual(main(['--version-info']), 0)
        self.assertNotIn('private-test-sentinel', stdout.getvalue())
        self.assertEqual(json.loads(stdout.getvalue()), metadata)


if __name__ == '__main__':
    unittest.main()
