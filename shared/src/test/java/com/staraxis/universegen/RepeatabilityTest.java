package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;
import com.staraxis.universegen.util.KryoSerializer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RepeatabilityTest {

    @Test
    void sameSeed_binaryEqual() throws IOException, GenerationException {
        UniverseGenConfig cfg = new UniverseGenConfig();
        cfg.setSeed(123); cfg.setSectorCount(10); cfg.setHexRadiusLy(5f); cfg.setStarToDeepSpaceRatio(0.5);

        GalaxyGenerator gen = new GalaxyGenerator();
        Galaxy g1 = gen.generate(cfg);
        Galaxy g2 = gen.generate(cfg);

        KryoSerializer ser = new KryoSerializer();
        byte[] a = toBytes(ser, g1);
        byte[] b = toBytes(ser, g2);
        assertArrayEquals(a, b, "Binary output should match for same seed");
    }

    private byte[] toBytes(KryoSerializer ser, Galaxy g) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ser.write(baos, g);
        return baos.toByteArray();
    }
}
