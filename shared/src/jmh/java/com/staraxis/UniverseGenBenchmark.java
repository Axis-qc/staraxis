package com.staraxis;

import com.staraxis.universegen.GalaxyGeneratorFacade;
import com.staraxis.universegen.config.UniverseGenConfig;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class UniverseGenBenchmark {

    private static final UniverseGenConfig CONFIG;
    static {
        try {
            File json = File.createTempFile("cfg", ".json");
            Files.writeString(json.toPath(), "{" +
                    "\"seed\":42,\"sectorCount\":1000,\"hexRadiusLy\":10,\"starToDeepSpaceRatio\":0.8}");
            CONFIG = UniverseGenConfig.load(json, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public void generateGalaxy() {
        GalaxyGeneratorFacade facade = new GalaxyGeneratorFacade();
        facade.generate(CONFIG);
    }
}
