package jaci.openrio.gradle.wpi.toolchain.discover

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider
import org.gradle.process.internal.DefaultExecActionFactory
import org.gradle.process.internal.ExecAction
import org.gradle.process.internal.ExecActionFactory
import org.gradle.process.internal.JavaExecAction
import org.gradle.util.TreeVisitor

@CompileStatic
abstract class AbstractToolchainDiscoverer {

    GccMetadataProvider metadataProvider
    Project project

    AbstractToolchainDiscoverer(Project project) {
        this.project = project
        FileResolver fileResolver = new IdentityFileResolver()
        ExecActionFactory execActionFactory = new DefaultExecActionFactory(fileResolver)
        this.metadataProvider = GccMetadataProvider.forGcc(execActionFactory)
    }

    abstract Optional<File> rootDir()

    boolean exists() {
        return metadata().isPresent()
    }

    Optional<File> binDir() {
        return join(rootDir(), "bin")
    }

    Optional<File> libDir() {
        return join(rootDir(), "lib")
    }

    Optional<File> includeDir() {
        return join(rootDir(), "include")
    }

    Optional<File> tool(String tool) {
        return join(binDir(), composeTool(tool))
    }

    Optional<File> gccFile() {
        return tool("g++")
    }

    Optional<File> gdbFile() {
        return tool("gdb")
    }

    Optional<File> sysroot() {
        if (OperatingSystem.current().isLinux())
            return Optional.empty()
        else
            return rootDir()
    }

    Optional<GccMetadata> metadata(TreeVisitor<? extends String> visitor = null) {
        return metadata(gccFile().orElse(null), visitor)
    }

    Optional<GccMetadata> metadata(File file, TreeVisitor<? extends String> visitor = null) {
        if (file == null || !file.exists())
            return Optional.empty()
        def searchresult = metadataProvider.getCompilerMetaData(file, [])
        if (visitor != null)
            searchresult.explain(visitor)
        return Optional.of(searchresult.component)
    }

    void explain(TreeVisitor<? extends String> visitor) {
        boolean exists = exists()
        visitor.node("Found: " + (exists ? "true" : "false"))

        visitor.with {
            node("Root: " + rootDir().orElse(null))
            node("Bin: " + binDir().orElse(null))
            node("Lib: " + libDir().orElse(null))
            node("Include: " + includeDir().orElse(null))
            node("Gcc: " + gccFile().orElse(null))
            node("Gdb: " + gdbFile().orElse(null))

            if (exists) {
                def meta = metadata().get()

                node("Metadata")
                startChildren()
                    node("Version: " + meta.version.toString())
                    node("Vendor: " + meta.vendor)
                    node("Default Arch: " + meta.defaultArchitecture.toString())

                    node("System Libraries")
                    startChildren()
                        def syslib = meta.systemLibraries
                        node("Include")
                        startChildren()
                        syslib.includeDirs.each { File f ->
                            node(f.absolutePath)
                        }
                        endChildren()

                        node("Lib Dirs")
                        startChildren()
                        syslib.libDirs.each { File f ->
                            node(f.absolutePath)
                        }
                        endChildren()

                        node("Macros")
                        startChildren()
                        syslib.preprocessorMacros.each { String k, String v ->
                            node(k + " = " + v)
                        }
                        endChildren()
                    endChildren()    // System Libraries
                endChildren() // Metadata
            } else {
                if (gccFile().isPresent()) {
                    node("Metadata Explain: ")
                    startChildren()
                    metadata(visitor)
                    endChildren()
                }
            }
        }
    }

    static String prefix() {
        return "arm-frc-linux-gnueabi-"
    }

    static String suffix() {
        return OperatingSystem.current().isWindows() ? ".exe" : ""
    }

    static String composeTool(String name) {
        return prefix() + name + suffix()
    }

    protected static Optional<File> join(Optional<File> f, String join) {
        return optFile(f.map({ File file -> new File(file, join) }).orElse(null))
    }

    protected static Optional<File> optFile(File f) {
        if (f == null || !f.exists())
            return Optional.empty()
        return Optional.of(f)
    }
}