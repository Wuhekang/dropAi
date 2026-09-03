# Generation log schema

```json
{
  "skillName": "ppt-generation",
  "skillVersion": "2.0.0",
  "skillHash": "sha256 of canonical packaged SKILL.md",
  "provider": "kimi_ark",
  "model": "configured provider model id",
  "providerStatus": "SUCCESS",
  "projectId": "",
  "major": "",
  "documentAnalysis": {
    "title": "",
    "identity": {},
    "headings": [],
    "excludedHeadings": [],
    "tableCount": 0
  },
  "assets": [
    {
      "id": "",
      "chapter": "",
      "page": 0,
      "type": "",
      "description": "",
      "path": ""
    }
  ],
  "slidePlan": [
    {
      "outputPage": 1,
      "pageType": "cover",
      "chapter": "",
      "assetIds": []
    }
  ],
  "template": {
    "id": "",
    "name": "",
    "priorityReason": "",
    "mappingLog": ""
  },
  "validation": {
    "status": "PASSED",
    "checks": [],
    "autoFixes": []
  }
}
```

Never persist the API key, Authorization header, raw environment variables, or another secret in this log. Write UTF-8 JSON beside the generated PPTX using the suffix `-generation-log.json`.
