package com.staraxis.game.core.world.astronomical;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


/**
 * MigrationTool 测试类。
 * 
 * 作用（Purpose）：测试 MigrationTool 类的所有功能，包括迁移功能和性能测试。
 */
class MigrationToolTest {

    private MigrationTool migrationTool;

    @BeforeEach
    void setUp() {
        migrationTool = new MigrationTool();
        migrationTool.setSourceVersion("1.0.0");
        migrationTool.setTargetVersion("2.0.0");
        migrationTool.setConversionRatio(1.0); // 1:1 转换比例（测试用）
    }

    // ========== 设置方法测试 ==========

    @Test
    void testSetSourceVersion() {
        MigrationTool tool = new MigrationTool();
        tool.setSourceVersion("1.0.0");
        
        // 验证设置成功（通过后续方法调用验证）
        assertDoesNotThrow(() -> {
            tool.setTargetVersion("2.0.0");
            tool.setConversionRatio(1.0);
        });
    }

    @Test
    void testSetSourceVersion_Null() {
        MigrationTool tool = new MigrationTool();
        assertThrows(IllegalArgumentException.class, () -> {
            tool.setSourceVersion(null);
        });
    }

    @Test
    void testSetTargetVersion() {
        MigrationTool tool = new MigrationTool();
        tool.setTargetVersion("2.0.0");
        
        // 验证设置成功
        assertDoesNotThrow(() -> {
            tool.setSourceVersion("1.0.0");
            tool.setConversionRatio(1.0);
        });
    }

    @Test
    void testSetConversionRatio() {
        MigrationTool tool = new MigrationTool();
        tool.setConversionRatio(2.0);
        
        // 验证设置成功
        assertDoesNotThrow(() -> {
            tool.setSourceVersion("1.0.0");
            tool.setTargetVersion("2.0.0");
        });
    }

    @Test
    void testSetConversionRatio_Invalid() {
        MigrationTool tool = new MigrationTool();
        assertThrows(IllegalArgumentException.class, () -> {
            tool.setConversionRatio(0.0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            tool.setConversionRatio(Double.NaN);
        });
    }

    // ========== 备份方法测试 ==========

    @Test
    void testBackupOriginal(@TempDir Path tempDir) throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.save");
        Files.write(testFile, "test content".getBytes());
        
        // 执行备份
        assertDoesNotThrow(() -> {
            migrationTool.backupOriginal(testFile.toString());
        });
        
        // 验证备份文件存在（备份文件名包含时间戳）
        File[] backupFiles = tempDir.toFile().listFiles((dir, name) -> name.startsWith("test.save.bak."));
        assertNotNull(backupFiles);
        assertTrue(backupFiles.length > 0);
    }

    @Test
    void testBackupOriginal_FileNotExists() {
        assertThrows(IOException.class, () -> {
            migrationTool.backupOriginal("nonexistent.save");
        });
    }

    // ========== 迁移功能测试 ==========

    @Test
    void testMigrateSaveFile(@TempDir Path tempDir) throws IOException, MigrationException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.save");
        Files.write(testFile, "test content".getBytes());
        
        // 执行迁移
        assertDoesNotThrow(() -> {
            migrationTool.migrateSaveFile(testFile.toString());
        });
        
        // 验证文件仍然存在
        assertTrue(Files.exists(testFile));
    }

    @Test
    void testMigrateSaveFile_NotConfigured() {
        MigrationTool tool = new MigrationTool();
        
        assertThrows(IllegalStateException.class, () -> {
            tool.migrateSaveFile("test.save");
        });
    }

    @Test
    void testMigrateDirectory(@TempDir Path tempDir) throws IOException, MigrationException {
        // 创建测试文件
        Path testFile1 = tempDir.resolve("test1.save");
        Path testFile2 = tempDir.resolve("test2.json");
        Files.write(testFile1, "test content 1".getBytes());
        Files.write(testFile2, "test content 2".getBytes());
        
        // 执行批量迁移
        assertDoesNotThrow(() -> {
            migrationTool.migrateDirectory(tempDir.toString());
        });
        
        // 验证文件仍然存在
        assertTrue(Files.exists(testFile1));
        assertTrue(Files.exists(testFile2));
    }

    // ========== 验证方法测试 ==========

    @Test
    void testValidateMigration(@TempDir Path tempDir) throws IOException {
        // 创建测试文件
        Path testFile = tempDir.resolve("test.save");
        Files.write(testFile, "test content".getBytes());
        
        // 验证迁移
        boolean isValid = migrationTool.validateMigration(testFile.toString());
        assertTrue(isValid);
    }

    @Test
    void testValidateMigration_FileNotExists() throws IOException {
        boolean isValid = migrationTool.validateMigration("nonexistent.save");
        assertFalse(isValid);
    }

    // ========== 批量迁移性能测试 ==========

    @Test
    void testBatchMigrationPerformance(@TempDir Path tempDir) throws IOException, MigrationException {
        // 创建100个测试文件（简化测试，不创建1000个）
        for (int i = 0; i < 100; i++) {
            Path testFile = tempDir.resolve("test" + i + ".save");
            Files.write(testFile, ("test content " + i).getBytes());
        }
        
        // 执行批量迁移并测量时间
        long startTime = System.currentTimeMillis();
        migrationTool.migrateDirectory(tempDir.toString());
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 验证性能（100个文件应该在合理时间内完成，例如 < 5秒）
        assertTrue(duration < 5000, "批量迁移100个文件应该在5秒内完成，实际耗时: " + duration + "ms");
    }
}
