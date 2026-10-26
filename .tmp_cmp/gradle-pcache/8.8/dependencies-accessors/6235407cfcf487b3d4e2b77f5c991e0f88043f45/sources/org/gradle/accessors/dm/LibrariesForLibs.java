package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the {@code libs} extension.
 */
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final AntlrLibraryAccessors laccForAntlrLibraryAccessors = new AntlrLibraryAccessors(owner);
    private final AsmLibraryAccessors laccForAsmLibraryAccessors = new AsmLibraryAccessors(owner);
    private final CommonsLibraryAccessors laccForCommonsLibraryAccessors = new CommonsLibraryAccessors(owner);
    private final FifeLibraryAccessors laccForFifeLibraryAccessors = new FifeLibraryAccessors(owner);
    private final FlatlafLibraryAccessors laccForFlatlafLibraryAccessors = new FlatlafLibraryAccessors(owner);
    private final GuiceLibraryAccessors laccForGuiceLibraryAccessors = new GuiceLibraryAccessors(owner);
    private final JacksonLibraryAccessors laccForJacksonLibraryAccessors = new JacksonLibraryAccessors(owner);
    private final JavaxLibraryAccessors laccForJavaxLibraryAccessors = new JavaxLibraryAccessors(owner);
    private final JetbrainsLibraryAccessors laccForJetbrainsLibraryAccessors = new JetbrainsLibraryAccessors(owner);
    private final JnaLibraryAccessors laccForJnaLibraryAccessors = new JnaLibraryAccessors(owner);
    private final LogbackLibraryAccessors laccForLogbackLibraryAccessors = new LogbackLibraryAccessors(owner);
    private final LwjglLibraryAccessors laccForLwjglLibraryAccessors = new LwjglLibraryAccessors(owner);
    private final OkhttpLibraryAccessors laccForOkhttpLibraryAccessors = new OkhttpLibraryAccessors(owner);
    private final RlLibraryAccessors laccForRlLibraryAccessors = new RlLibraryAccessors(owner);
    private final RsLibraryAccessors laccForRsLibraryAccessors = new RsLibraryAccessors(owner);
    private final Slf4jLibraryAccessors laccForSlf4jLibraryAccessors = new Slf4jLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

    /**
     * Dependency provider for <b>fastutil</b> with <b>it.unimi.dsi:fastutil</b> coordinates and
     * with version reference <b>fastutil</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getFastutil() {
        return create("fastutil");
    }

    /**
     * Dependency provider for <b>findbugs</b> with <b>com.google.code.findbugs:jsr305</b> coordinates and
     * with version <b>3.0.2</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getFindbugs() {
        return create("findbugs");
    }

    /**
     * Dependency provider for <b>gson</b> with <b>com.google.code.gson:gson</b> coordinates and
     * with version <b>2.8.5</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getGson() {
        return create("gson");
    }

    /**
     * Dependency provider for <b>guava</b> with <b>com.google.guava:guava</b> coordinates and
     * with version <b>23.2-jre</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getGuava() {
        return create("guava");
    }

    /**
     * Dependency provider for <b>hamcrest</b> with <b>org.hamcrest:hamcrest-library</b> coordinates and
     * with version <b>1.3</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getHamcrest() {
        return create("hamcrest");
    }

    /**
     * Dependency provider for <b>jopt</b> with <b>net.sf.jopt-simple:jopt-simple</b> coordinates and
     * with version <b>5.0.1</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getJopt() {
        return create("jopt");
    }

    /**
     * Dependency provider for <b>junit</b> with <b>junit:junit</b> coordinates and
     * with version <b>4.12</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getJunit() {
        return create("junit");
    }

    /**
     * Dependency provider for <b>mockito</b> with <b>org.mockito:mockito-core</b> coordinates and
     * with version <b>5.23.0</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getMockito() {
        return create("mockito");
    }

    /**
     * Dependency provider for <b>protobuf</b> with <b>com.google.protobuf:protobuf-javalite</b> coordinates and
     * with version <b>3.21.12</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getProtobuf() {
        return create("protobuf");
    }

    /**
     * Group of libraries at <b>antlr</b>
     */
    public AntlrLibraryAccessors getAntlr() {
        return laccForAntlrLibraryAccessors;
    }

    /**
     * Group of libraries at <b>asm</b>
     */
    public AsmLibraryAccessors getAsm() {
        return laccForAsmLibraryAccessors;
    }

    /**
     * Group of libraries at <b>commons</b>
     */
    public CommonsLibraryAccessors getCommons() {
        return laccForCommonsLibraryAccessors;
    }

    /**
     * Group of libraries at <b>fife</b>
     */
    public FifeLibraryAccessors getFife() {
        return laccForFifeLibraryAccessors;
    }

    /**
     * Group of libraries at <b>flatlaf</b>
     */
    public FlatlafLibraryAccessors getFlatlaf() {
        return laccForFlatlafLibraryAccessors;
    }

    /**
     * Group of libraries at <b>guice</b>
     */
    public GuiceLibraryAccessors getGuice() {
        return laccForGuiceLibraryAccessors;
    }

    /**
     * Group of libraries at <b>jackson</b>
     */
    public JacksonLibraryAccessors getJackson() {
        return laccForJacksonLibraryAccessors;
    }

    /**
     * Group of libraries at <b>javax</b>
     */
    public JavaxLibraryAccessors getJavax() {
        return laccForJavaxLibraryAccessors;
    }

    /**
     * Group of libraries at <b>jetbrains</b>
     */
    public JetbrainsLibraryAccessors getJetbrains() {
        return laccForJetbrainsLibraryAccessors;
    }

    /**
     * Group of libraries at <b>jna</b>
     */
    public JnaLibraryAccessors getJna() {
        return laccForJnaLibraryAccessors;
    }

    /**
     * Group of libraries at <b>logback</b>
     */
    public LogbackLibraryAccessors getLogback() {
        return laccForLogbackLibraryAccessors;
    }

    /**
     * Group of libraries at <b>lwjgl</b>
     */
    public LwjglLibraryAccessors getLwjgl() {
        return laccForLwjglLibraryAccessors;
    }

    /**
     * Group of libraries at <b>okhttp</b>
     */
    public OkhttpLibraryAccessors getOkhttp() {
        return laccForOkhttpLibraryAccessors;
    }

    /**
     * Group of libraries at <b>rl</b>
     */
    public RlLibraryAccessors getRl() {
        return laccForRlLibraryAccessors;
    }

    /**
     * Group of libraries at <b>rs</b>
     */
    public RsLibraryAccessors getRs() {
        return laccForRsLibraryAccessors;
    }

    /**
     * Group of libraries at <b>slf4j</b>
     */
    public Slf4jLibraryAccessors getSlf4j() {
        return laccForSlf4jLibraryAccessors;
    }

    /**
     * Group of versions at <b>versions</b>
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Group of bundles at <b>bundles</b>
     */
    public BundleAccessors getBundles() {
        return baccForBundleAccessors;
    }

    /**
     * Group of plugins at <b>plugins</b>
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class AntlrLibraryAccessors extends SubDependencyFactory {

        public AntlrLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>org.antlr:antlr4</b> coordinates and
         * with version reference <b>antlr</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("antlr.core");
        }

        /**
         * Dependency provider for <b>runtime</b> with <b>org.antlr:antlr4-runtime</b> coordinates and
         * with version reference <b>antlr</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getRuntime() {
            return create("antlr.runtime");
        }

    }

    public static class AsmLibraryAccessors extends SubDependencyFactory {

        public AsmLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>commons</b> with <b>org.ow2.asm:asm-commons</b> coordinates and
         * with version reference <b>asm</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCommons() {
            return create("asm.commons");
        }

        /**
         * Dependency provider for <b>core</b> with <b>org.ow2.asm:asm</b> coordinates and
         * with version reference <b>asm</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("asm.core");
        }

        /**
         * Dependency provider for <b>tree</b> with <b>org.ow2.asm:asm-tree</b> coordinates and
         * with version reference <b>asm</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getTree() {
            return create("asm.tree");
        }

        /**
         * Dependency provider for <b>util</b> with <b>org.ow2.asm:asm-util</b> coordinates and
         * with version reference <b>asm</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getUtil() {
            return create("asm.util");
        }

    }

    public static class CommonsLibraryAccessors extends SubDependencyFactory {

        public CommonsLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>cli</b> with <b>commons-cli:commons-cli</b> coordinates and
         * with version <b>1.3.1</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCli() {
            return create("commons.cli");
        }

        /**
         * Dependency provider for <b>compress</b> with <b>org.apache.commons:commons-compress</b> coordinates and
         * with version <b>1.10</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCompress() {
            return create("commons.compress");
        }

        /**
         * Dependency provider for <b>text</b> with <b>org.apache.commons:commons-text</b> coordinates and
         * with version <b>1.2</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getText() {
            return create("commons.text");
        }

    }

    public static class FifeLibraryAccessors extends SubDependencyFactory {

        public FifeLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>autocomplete</b> with <b>com.fifesoft:autocomplete</b> coordinates and
         * with version <b>3.1.1</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAutocomplete() {
            return create("fife.autocomplete");
        }

        /**
         * Dependency provider for <b>rsyntaxtextarea</b> with <b>com.fifesoft:rsyntaxtextarea</b> coordinates and
         * with version <b>3.1.2</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getRsyntaxtextarea() {
            return create("fife.rsyntaxtextarea");
        }

    }

    public static class FlatlafLibraryAccessors extends SubDependencyFactory {

        public FlatlafLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>net.runelite:flatlaf</b> coordinates and
         * with version reference <b>flatlaf</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("flatlaf.core");
        }

        /**
         * Dependency provider for <b>extras</b> with <b>net.runelite:flatlaf-extras</b> coordinates and
         * with version reference <b>flatlaf</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getExtras() {
            return create("flatlaf.extras");
        }

    }

    public static class GuiceLibraryAccessors extends SubDependencyFactory {

        public GuiceLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>com.google.inject:guice</b> coordinates and
         * with version reference <b>guice</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("guice.core");
        }

        /**
         * Dependency provider for <b>grapher</b> with <b>com.google.inject.extensions:guice-grapher</b> coordinates and
         * with version reference <b>guice</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getGrapher() {
            return create("guice.grapher");
        }

        /**
         * Dependency provider for <b>testlib</b> with <b>com.google.inject.extensions:guice-testlib</b> coordinates and
         * with version reference <b>guice</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getTestlib() {
            return create("guice.testlib");
        }

    }

    public static class JacksonLibraryAccessors extends SubDependencyFactory {

        public JacksonLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>databind</b> with <b>com.fasterxml.jackson.core:jackson-databind</b> coordinates and
         * with version reference <b>jackson</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getDatabind() {
            return create("jackson.databind");
        }

    }

    public static class JavaxLibraryAccessors extends SubDependencyFactory {

        public JavaxLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>inject</b> with <b>javax.inject:javax.inject</b> coordinates and
         * with version <b>1</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getInject() {
            return create("javax.inject");
        }

    }

    public static class JetbrainsLibraryAccessors extends SubDependencyFactory {

        public JetbrainsLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>annotations</b> with <b>org.jetbrains:annotations</b> coordinates and
         * with version <b>23.0.0</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAnnotations() {
            return create("jetbrains.annotations");
        }

    }

    public static class JnaLibraryAccessors extends SubDependencyFactory {

        public JnaLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>net.java.dev.jna:jna</b> coordinates and
         * with version <b>5.9.0</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("jna.core");
        }

        /**
         * Dependency provider for <b>platform</b> with <b>net.java.dev.jna:jna-platform</b> coordinates and
         * with version <b>5.9.0</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getPlatform() {
            return create("jna.platform");
        }

    }

    public static class LogbackLibraryAccessors extends SubDependencyFactory {

        public LogbackLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>classic</b> with <b>ch.qos.logback:logback-classic</b> coordinates and
         * with version reference <b>logback</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getClassic() {
            return create("logback.classic");
        }

        /**
         * Dependency provider for <b>core</b> with <b>ch.qos.logback:logback-core</b> coordinates and
         * with version reference <b>logback</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("logback.core");
        }

    }

    public static class LwjglLibraryAccessors extends SubDependencyFactory {

        public LwjglLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>core</b> with <b>org.lwjgl:lwjgl</b> coordinates and
         * with version reference <b>lwjgl</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCore() {
            return create("lwjgl.core");
        }

        /**
         * Dependency provider for <b>opencl</b> with <b>org.lwjgl:lwjgl-opencl</b> coordinates and
         * with version reference <b>lwjgl</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getOpencl() {
            return create("lwjgl.opencl");
        }

        /**
         * Dependency provider for <b>opengl</b> with <b>org.lwjgl:lwjgl-opengl</b> coordinates and
         * with version reference <b>lwjgl</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getOpengl() {
            return create("lwjgl.opengl");
        }

    }

    public static class OkhttpLibraryAccessors extends SubDependencyFactory {

        public OkhttpLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>mockserver</b> with <b>com.squareup.okhttp3:mockwebserver</b> coordinates and
         * with version <b>3.14.9</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getMockserver() {
            return create("okhttp.mockserver");
        }

    }

    public static class RlLibraryAccessors extends SubDependencyFactory {
        private final RlHttpLibraryAccessors laccForRlHttpLibraryAccessors = new RlHttpLibraryAccessors(owner);

        public RlLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>awt</b> with <b>net.runelite:rlawt</b> coordinates and
         * with version <b>1.8</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getAwt() {
            return create("rl.awt");
        }

        /**
         * Dependency provider for <b>discord</b> with <b>net.runelite:discord</b> coordinates and
         * with version <b>1.4</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getDiscord() {
            return create("rl.discord");
        }

        /**
         * Dependency provider for <b>jagexauth</b> with <b>net.runelite:jagexauth</b> coordinates and
         * with version <b>0.3</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJagexauth() {
            return create("rl.jagexauth");
        }

        /**
         * Dependency provider for <b>orange</b> with <b>net.runelite:orange-extensions</b> coordinates and
         * with version <b>1.1</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getOrange() {
            return create("rl.orange");
        }

        /**
         * Group of libraries at <b>rl.http</b>
         */
        public RlHttpLibraryAccessors getHttp() {
            return laccForRlHttpLibraryAccessors;
        }

    }

    public static class RlHttpLibraryAccessors extends SubDependencyFactory {

        public RlHttpLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>api</b> with <b>net.runelite.arn:http-api</b> coordinates and
         * with version <b>1.2.26</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getApi() {
            return create("rl.http.api");
        }

    }

    public static class RsLibraryAccessors extends SubDependencyFactory {

        public RsLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>cache</b> with <b>net.runelite.rs:cache</b> coordinates and
         * with version reference <b>cache</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getCache() {
            return create("rs.cache");
        }

    }

    public static class Slf4jLibraryAccessors extends SubDependencyFactory {

        public Slf4jLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>api</b> with <b>org.slf4j:slf4j-api</b> coordinates and
         * with version reference <b>slf4j</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getApi() {
            return create("slf4j.api");
        }

        /**
         * Dependency provider for <b>simple</b> with <b>org.slf4j:slf4j-simple</b> coordinates and
         * with version reference <b>slf4j</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getSimple() {
            return create("slf4j.simple");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>antlr</b> with value <b>4.13.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAntlr() { return getVersion("antlr"); }

        /**
         * Version alias <b>asm</b> with value <b>9.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getAsm() { return getVersion("asm"); }

        /**
         * Version alias <b>cache</b> with value <b>165</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getCache() { return getVersion("cache"); }

        /**
         * Version alias <b>fastutil</b> with value <b>8.5.12</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getFastutil() { return getVersion("fastutil"); }

        /**
         * Version alias <b>flatlaf</b> with value <b>3.2.5-rl4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getFlatlaf() { return getVersion("flatlaf"); }

        /**
         * Version alias <b>guice</b> with value <b>4.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getGuice() { return getVersion("guice"); }

        /**
         * Version alias <b>jackson</b> with value <b>2.17.2</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJackson() { return getVersion("jackson"); }

        /**
         * Version alias <b>logback</b> with value <b>1.2.9</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLogback() { return getVersion("logback"); }

        /**
         * Version alias <b>lombok</b> with value <b>1.18.30</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLombok() { return getVersion("lombok"); }

        /**
         * Version alias <b>lwjgl</b> with value <b>3.3.2</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getLwjgl() { return getVersion("lwjgl"); }

        /**
         * Version alias <b>slf4j</b> with value <b>1.7.25</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSlf4j() { return getVersion("slf4j"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

    }

    public static class PluginAccessors extends PluginFactory {

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Plugin provider for <b>lombok</b> with plugin id <b>io.freefair.lombok</b> and
         * with version <b>8.10.2</b>
         * <p>
         * This plugin was declared in catalog libs.versions.toml
         */
        public Provider<PluginDependency> getLombok() { return createPlugin("lombok"); }

    }

}
