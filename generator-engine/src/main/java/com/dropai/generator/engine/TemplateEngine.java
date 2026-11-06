package com.dropai.generator.engine;

import com.dropai.generator.blueprint.BlueprintPipeline;
import com.dropai.generator.model.GenerationModel;
import com.dropai.generator.model.GenerationModel.FileGenerationPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.ClassTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TemplateEngine implements AutoCloseable {
    private final Configuration configuration;
    private final ExecutorService executor = new ThreadPoolExecutor(8, 16, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
    private final ObjectMapper mapper = new ObjectMapper();

    public TemplateEngine() {
        configuration = new Configuration(Configuration.VERSION_2_3_33);
        configuration.setTemplateLoader(new ClassTemplateLoader(TemplateEngine.class, "/templates/springboot-vue-template/1.0.0/dynamic"));
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
    }

    public Path generate(GenerationModel model, Path outputParent) throws Exception {
        Path root = outputParent.toAbsolutePath().normalize().resolve(model.source().project().name()).normalize();
        requireUnder(outputParent.toAbsolutePath().normalize(), root);
        if (Files.exists(root)) throw new IllegalStateException("OUTPUT_ALREADY_EXISTS: " + root);
        Path staging = outputParent.toAbsolutePath().normalize().resolve("." + model.source().project().name() + ".staging-" + UUID.randomUUID()).normalize();
        requireUnder(outputParent.toAbsolutePath().normalize(), staging);
        Files.createDirectories(staging);
        try {
            ProjectScaffold.write(staging, model);
            List<CompletableFuture<ManifestFile>> futures = model.filePlans().stream()
                    .map(plan -> CompletableFuture.supplyAsync(() -> render(staging, plan), executor)).toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<ManifestFile> files = new ArrayList<>(futures.stream().map(CompletableFuture::join).toList());
            write(staging, "README.md", ProjectScaffold.readme(model));
            try (var paths = Files.walk(staging)) {
                paths.filter(Files::isRegularFile).filter(path -> files.stream().noneMatch(f -> staging.resolve(f.path()).equals(path)))
                        .forEach(path -> files.add(manifest(staging, path, "static:" + staging.relativize(path).toString().replace('\\','/'), model.blueprintHash())));
            }
            files.sort(Comparator.comparing(ManifestFile::path));
            List<ManifestPermission> permissions = model.permissions().stream()
                    .map(permission -> new ManifestPermission(permission.moduleCode(), permission.action(), permission.code()))
                    .toList();
            Manifest manifest = new Manifest("1", model.source().project().name(), model.blueprintHash(),
                    new TemplateRef("springboot-vue-template", "1.0.0"), "1.0.0", permissions, files);
            write(staging, "manifest.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest));
            Files.move(staging, root, StandardCopyOption.ATOMIC_MOVE);
            zip(root, outputParent.resolve(model.source().project().name() + ".zip"));
            return root;
        } catch (Exception e) {
            deleteTree(staging);
            throw e;
        }
    }

    private ManifestFile render(Path staging, FileGenerationPlan plan) {
        try {
            Path target = staging.resolve(plan.outputPath()).normalize();
            requireUnder(staging, target);
            Files.createDirectories(target.getParent());
            StringWriter out = new StringWriter();
            configuration.getTemplate(plan.templateName()).process(plan.templateModel(), out);
            Path temp = Files.createTempFile(target.getParent(), ".render-", ".tmp");
            Files.writeString(temp, out.toString(), StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return manifest(staging, target, plan.sourceKey(), plan.modelHash());
        } catch (Exception e) { throw new CompletionException(new IllegalStateException("RENDER_FAILED " + plan.sourceKey(), e)); }
    }

    private static ManifestFile manifest(Path root, Path file, String sourceKey, String modelHash) {
        try { byte[] bytes=Files.readAllBytes(file); String hash=BlueprintPipeline.sha256(bytes); return new ManifestFile(root.relativize(file).toString().replace('\\','/'),hash,sourceKey,modelHash,"GENERATED",hash,false); }
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void write(Path root,String relative,String content)throws Exception{Path target=root.resolve(relative).normalize();requireUnder(root,target);Files.createDirectories(target.getParent());Files.writeString(target,content,StandardCharsets.UTF_8);}
    private static void zip(Path root,Path zip)throws Exception{try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(zip))){try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).toList()){String name=root.getFileName()+"/"+root.relativize(path).toString().replace('\\','/');out.putNextEntry(new ZipEntry(name));Files.copy(path,out);out.closeEntry();}}}}
    private static void requireUnder(Path root,Path target){if(!target.normalize().startsWith(root.normalize()))throw new IllegalArgumentException("PATH_TRAVERSAL");}
    private static void deleteTree(Path root){try{if(Files.notExists(root))return;try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}catch(Exception ignored){}}
    @Override public void close(){executor.shutdown();}
    public record TemplateRef(String id,String version){}
    public record Manifest(String manifestVersion,String project,String blueprintHash,TemplateRef template,String engineVersion,List<ManifestPermission> permissions,List<ManifestFile> files){}
    public record ManifestPermission(String module,String action,String code){}
    public record ManifestFile(String path,String sha256,String sourceKey,String modelHash,String ownership,String lastGeneratedHash,boolean userModified){}
}
