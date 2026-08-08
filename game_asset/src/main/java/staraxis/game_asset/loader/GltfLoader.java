package staraxis.game_asset.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game_asset.data.MaterialData;
import staraxis.game_asset.data.MeshData;
import staraxis.game_asset.data.VertexAttributeType;
import staraxis.game_asset.data.VertexLayout;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * GltfLoader（glTF 2.0 文件加载器，零渲染依赖）。
 *
 * 解析 .gltf JSON + .bin 二进制缓冲区，输出中性 {@link MeshData} + {@link MaterialData}。
 *
 * 当前支持范围（T2.6）：
 * - 静态网格：POSITION + NORMAL + TEXCOORD_0
 * - 三角形索引（UNSIGNED_SHORT / UNSIGNED_INT）
 * - PBR 材质：baseColorTexture / normalTexture / emissiveTexture / specularTexture
 * - 贴图路径解析为相对于 assets 根目录的完整路径
 *
 * 暂不支持（T2.12）：
 * - 骨骼蒙皮（JOINTS_0 / WEIGHTS_0 跳过）
 * - 动画
 * - 形变目标（morph targets）
 *
 * 调用方（client 层）负责读取 .gltf 和 .bin 文件为流/字节数组传入，
 * 并提供 basePath（如 "ship/star_eater/"）用于拼接贴图相对路径。
 */
public final class GltfLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GltfLoader() {
    }

    // glTF componentType 常量
    private static final int GLTF_UNSIGNED_BYTE = 5121;
    private static final int GLTF_UNSIGNED_SHORT = 5123;
    private static final int GLTF_UNSIGNED_INT = 5125;
    private static final int GLTF_FLOAT = 5126;

    // glTF type 分量数
    private static final int SCALAR_COUNT = 1;
    private static final int VEC2_COUNT = 2;
    private static final int VEC3_COUNT = 3;
    private static final int VEC4_COUNT = 4;

    /**
     * 加载 glTF 模型。
     *
     * @param gltfStream .gltf 文件输入流
     * @param binData    .bin 文件二进制数据
     * @param basePath   glTF 文件所在目录相对路径（如 "ship/star_eater/"，末尾带斜杠）
     * @return 加载结果（MeshData + MaterialData）
     * @throws RuntimeException 解析失败时抛出
     */
    public static LoadedModel load(InputStream gltfStream, byte[] binData, String basePath) {
        try {
            JsonNode root = MAPPER.readTree(gltfStream);
            ByteBuffer buffer = ByteBuffer.wrap(binData).order(ByteOrder.LITTLE_ENDIAN);

            // 解析网格
            MeshData mesh = parseMesh(root, buffer);

            // 解析材质
            MaterialData material = parseMaterial(root, basePath);

            return new LoadedModel(mesh, material);
        } catch (Exception e) {
            throw new RuntimeException("glTF 加载失败: " + e.getMessage(), e);
        }
    }

    // ── 网格解析 ──────────────────────────────────────────

    /**
     * 解析第一个 mesh 的第一个 primitive。
     *
     * glTF 文件可含多个 mesh，每个 mesh 可含多个 primitive（子网格）。
     * 当前只处理第一个 mesh 的第一个 primitive。
     */
    private static MeshData parseMesh(JsonNode root, ByteBuffer buffer) {
        JsonNode meshNode = root.path("meshes").path(0);
        JsonNode primitive = meshNode.path("primitives").path(0);
        JsonNode attributes = primitive.path("attributes");

        // 确定顶点布局
        boolean hasPosition = attributes.has("POSITION");
        boolean hasNormal = attributes.has("NORMAL");
        boolean hasTexcoord = attributes.has("TEXCOORD_0");
        boolean hasTangent = attributes.has("TANGENT");

        if (!hasPosition) {
            throw new RuntimeException("glTF mesh 缺少 POSITION 属性");
        }

        // 构建 VertexLayout（TANGENT：glTF 有则用，无则由加载器计算；normal mapping 需要）
        int attrCount = 1; // POSITION
        if (hasNormal) attrCount++;
        if (hasTexcoord) attrCount++;
        if (hasNormal && hasTexcoord) attrCount++; // TANGENT（需 normal + uv 才能计算）
        VertexAttributeType[] attrTypes = new VertexAttributeType[attrCount];
        int ai = 0;
        attrTypes[ai++] = VertexAttributeType.POSITION;
        if (hasNormal) attrTypes[ai++] = VertexAttributeType.NORMAL;
        if (hasTexcoord) attrTypes[ai++] = VertexAttributeType.TEXCOORD_0;
        if (hasNormal && hasTexcoord) attrTypes[ai++] = VertexAttributeType.TANGENT;
        VertexLayout layout = new VertexLayout(attrTypes);

        // POSITION accessor 决定顶点数
        int positionAccessorIdx = attributes.get("POSITION").asInt();
        JsonNode posAccessor = root.path("accessors").get(positionAccessorIdx);
        int vertexCount = posAccessor.get("count").asInt();

        // 读取索引
        int[] indices = parseIndices(root, buffer, primitive);

        // 读取原始属性（独立数组，供 TANGENT 计算用）
        float[] positions = readFloatAttribute(root, buffer, positionAccessorIdx, VEC3_COUNT, vertexCount);
        float[] normals = hasNormal
                ? readFloatAttribute(root, buffer, attributes.get("NORMAL").asInt(), VEC3_COUNT, vertexCount)
                : null;
        float[] texcoords = hasTexcoord
                ? readFloatAttribute(root, buffer, attributes.get("TEXCOORD_0").asInt(), VEC2_COUNT, vertexCount)
                : null;

        // 读取或计算 TANGENT
        float[] tangents = null;
        if (hasNormal && hasTexcoord) {
            if (hasTangent) {
                tangents = readFloatAttribute(root, buffer, attributes.get("TANGENT").asInt(), VEC4_COUNT, vertexCount);
            } else {
                tangents = computeTangents(positions, normals, texcoords, indices, vertexCount);
            }
        }

        // 交错写入顶点数组
        int stride = layout.stride();
        float[] vertices = new float[vertexCount * stride];
        int[] offsets = new int[VertexAttributeType.values().length];
        int vo = 0;
        for (VertexAttributeType type : layout.attributes()) {
            offsets[type.ordinal()] = vo;
            vo += type.componentCount;
        }

        for (int i = 0; i < vertexCount; i++) {
            int base = i * stride;
            // POSITION
            System.arraycopy(positions, i * 3, vertices, base + offsets[VertexAttributeType.POSITION.ordinal()], 3);
            // NORMAL
            if (normals != null) {
                System.arraycopy(normals, i * 3, vertices, base + offsets[VertexAttributeType.NORMAL.ordinal()], 3);
            }
            // TEXCOORD_0
            if (texcoords != null) {
                System.arraycopy(texcoords, i * 2, vertices, base + offsets[VertexAttributeType.TEXCOORD_0.ordinal()], 2);
            }
            // TANGENT
            if (tangents != null) {
                System.arraycopy(tangents, i * 4, vertices, base + offsets[VertexAttributeType.TANGENT.ordinal()], 4);
            }
        }

        return new MeshData(layout, vertices, indices);
    }

    /**
     * 计算 TANGENT 属性（glTF 规范 Morley 方法，MikkTSpace 近似）。
     *
     * glTF 模型通常不导出 TANGENT，需要在加载时根据 position/normal/uv 推导：
     * 1. 对每个三角形，根据边向量和 UV 差计算切线/副切线
     * 2. 按顶点累积所有相邻三角形的切线贡献
     * 3. 对每个顶点正交化（Gram-Schmidt 相对法线）+ 计算 bitangent 符号 w
     *
     * @param positions  顶点位置（VEC3，length = count*3）
     * @param normals    顶点法线（VEC3）
     * @param texcoords  纹理坐标（VEC2）
     * @param indices    索引（三角形）
     * @param vertexCount 顶点数
     * @return TANGENT（VEC4：xyz 切线 + w bitangent 符号）
     */
    private static float[] computeTangents(float[] positions, float[] normals, float[] texcoords,
                                           int[] indices, int vertexCount) {
        // 累积缓冲区
        float[] tanSum = new float[vertexCount * 3];
        float[] bitanSum = new float[vertexCount * 3];

        for (int i = 0; i < indices.length; i += 3) {
            int i0 = indices[i];
            int i1 = indices[i + 1];
            int i2 = indices[i + 2];

            // 边向量
            float p1x = positions[i1 * 3] - positions[i0 * 3];
            float p1y = positions[i1 * 3 + 1] - positions[i0 * 3 + 1];
            float p1z = positions[i1 * 3 + 2] - positions[i0 * 3 + 2];
            float p2x = positions[i2 * 3] - positions[i0 * 3];
            float p2y = positions[i2 * 3 + 1] - positions[i0 * 3 + 1];
            float p2z = positions[i2 * 3 + 2] - positions[i0 * 3 + 2];

            // UV 差
            float u1x = texcoords[i1 * 2] - texcoords[i0 * 2];
            float u1y = texcoords[i1 * 2 + 1] - texcoords[i0 * 2 + 1];
            float u2x = texcoords[i2 * 2] - texcoords[i0 * 2];
            float u2y = texcoords[i2 * 2 + 1] - texcoords[i0 * 2 + 1];

            // 逆行列（UV 差矩阵求逆）
            float r = 1.0f / (u1x * u2y - u1y * u2x);

            // 切线/副切线（世界空间）
            float tx = (p1x * u2y - p2x * u1y) * r;
            float ty = (p1y * u2y - p2y * u1y) * r;
            float tz = (p1z * u2y - p2z * u1y) * r;
            float bx = (p2x * u1x - p1x * u2x) * r;
            float by = (p2y * u1x - p1y * u2x) * r;
            float bz = (p2z * u1x - p1z * u2x) * r;

            // 累积到三个顶点
            tanSum[i0 * 3] += tx;
            tanSum[i0 * 3 + 1] += ty;
            tanSum[i0 * 3 + 2] += tz;
            tanSum[i1 * 3] += tx;
            tanSum[i1 * 3 + 1] += ty;
            tanSum[i1 * 3 + 2] += tz;
            tanSum[i2 * 3] += tx;
            tanSum[i2 * 3 + 1] += ty;
            tanSum[i2 * 3 + 2] += tz;

            bitanSum[i0 * 3] += bx;
            bitanSum[i0 * 3 + 1] += by;
            bitanSum[i0 * 3 + 2] += bz;
            bitanSum[i1 * 3] += bx;
            bitanSum[i1 * 3 + 1] += by;
            bitanSum[i1 * 3 + 2] += bz;
            bitanSum[i2 * 3] += bx;
            bitanSum[i2 * 3 + 1] += by;
            bitanSum[i2 * 3 + 2] += bz;
        }

        // 逐顶点正交化 + bitangent 符号
        float[] tangents = new float[vertexCount * 4];
        for (int i = 0; i < vertexCount; i++) {
            float nx = normals[i * 3];
            float ny = normals[i * 3 + 1];
            float nz = normals[i * 3 + 2];

            float tx = tanSum[i * 3];
            float ty = tanSum[i * 3 + 1];
            float tz = tanSum[i * 3 + 2];

            // Gram-Schmidt 正交化：T = normalize(T - N * dot(N, T))
            float dot = nx * tx + ny * ty + nz * tz;
            tx -= nx * dot;
            ty -= ny * dot;
            tz -= nz * dot;
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len > 1e-6f) {
                tx /= len;
                ty /= len;
                tz /= len;
            }

            // w = sign(dot(cross(N, T), B))
            float cx = ny * tz - nz * ty;
            float cy = nz * tx - nx * tz;
            float cz = nx * ty - ny * tx;
            float w = (cx * bitanSum[i * 3] + cy * bitanSum[i * 3 + 1] + cz * bitanSum[i * 3 + 2]) < 0 ? -1f : 1f;

            tangents[i * 4] = tx;
            tangents[i * 4 + 1] = ty;
            tangents[i * 4 + 2] = tz;
            tangents[i * 4 + 3] = w;
        }

        return tangents;
    }

    /**
     * 从 accessor 读取 FLOAT 类型顶点属性，返回独立数组。
     *
     * @param root          glTF JSON 根节点
     * @param buffer        二进制缓冲区
     * @param accessorIdx   accessor 索引
     * @param componentCount 每元素的 float 分量数（VEC2=2, VEC3=3, VEC4=4）
     * @param vertexCount   顶点数
     * @return 属性数据（length = vertexCount * componentCount）
     */
    private static float[] readFloatAttribute(JsonNode root, ByteBuffer buffer, int accessorIdx,
                                              int componentCount, int vertexCount) {
        JsonNode accessor = root.path("accessors").get(accessorIdx);
        int bufferViewIdx = accessor.get("bufferView").asInt();
        int accessorByteOffset = accessor.path("byteOffset").asInt(0);

        JsonNode bufferView = root.path("bufferViews").get(bufferViewIdx);
        int bvByteOffset = bufferView.get("byteOffset").asInt();
        int bvByteStride = bufferView.path("byteStride").asInt(0);
        int componentSize = 4; // FLOAT = 4 bytes
        int attrByteSize = componentCount * componentSize;

        // 如果 bufferView 有 byteStride，用它；否则用属性自身大小
        int actualStride = bvByteStride > 0 ? bvByteStride : attrByteSize;

        float[] data = new float[vertexCount * componentCount];
        for (int i = 0; i < vertexCount; i++) {
            int bytePos = bvByteOffset + accessorByteOffset + i * actualStride;
            for (int c = 0; c < componentCount; c++) {
                data[i * componentCount + c] = buffer.getFloat(bytePos + c * componentSize);
            }
        }
        return data;
    }

    /**
     * 解析索引数据。
     *
     * @param root      glTF JSON 根节点
     * @param buffer    二进制缓冲区
     * @param primitive mesh primitive 节点
     * @return 索引数组（int[]）
     */
    private static int[] parseIndices(JsonNode root, ByteBuffer buffer, JsonNode primitive) {
        int indicesAccessorIdx = primitive.get("indices").asInt();
        JsonNode accessor = root.path("accessors").get(indicesAccessorIdx);
        int count = accessor.get("count").asInt();
        int componentType = accessor.get("componentType").asInt();
        int bufferViewIdx = accessor.get("bufferView").asInt();
        int accessorByteOffset = accessor.path("byteOffset").asInt(0);

        JsonNode bufferView = root.path("bufferViews").get(bufferViewIdx);
        int bvByteOffset = bufferView.get("byteOffset").asInt();

        int[] indices = new int[count];

        switch (componentType) {
            case GLTF_UNSIGNED_SHORT -> {
                for (int i = 0; i < count; i++) {
                    indices[i] = buffer.getShort(bvByteOffset + accessorByteOffset + i * 2) & 0xFFFF;
                }
            }
            case GLTF_UNSIGNED_INT -> {
                for (int i = 0; i < count; i++) {
                    indices[i] = buffer.getInt(bvByteOffset + accessorByteOffset + i * 4);
                }
            }
            case GLTF_UNSIGNED_BYTE -> {
                for (int i = 0; i < count; i++) {
                    indices[i] = buffer.get(bvByteOffset + accessorByteOffset + i) & 0xFF;
                }
            }
            default -> throw new RuntimeException("不支持的索引 componentType: " + componentType);
        }

        return indices;
    }

    // ── 材质解析 ──────────────────────────────────────────

    /**
     * 解析第一个材质，提取贴图路径和 PBR 参数。
     *
     * @param root     glTF JSON 根节点
     * @param basePath glTF 文件所在目录（用于拼接贴图相对路径）
     * @return 材质数据
     */
    private static MaterialData parseMaterial(JsonNode root, String basePath) {
        JsonNode materials = root.path("materials");
        if (materials.isMissingNode() || materials.size() == 0) {
            return new MaterialData();
        }

        JsonNode mat = materials.get(0);
        MaterialData data = new MaterialData();

        // PBR 基础颜色贴图
        JsonNode pbr = mat.path("pbrMetallicRoughness");
        if (pbr.has("baseColorTexture")) {
            int texIdx = pbr.path("baseColorTexture").get("index").asInt();
            data.baseColorTexturePath = resolveTexturePath(root, texIdx, basePath);
        }

        // 金属粗糙度贴图
        if (pbr.has("metallicRoughnessTexture")) {
            int texIdx = pbr.path("metallicRoughnessTexture").get("index").asInt();
            data.metallicRoughnessTexturePath = resolveTexturePath(root, texIdx, basePath);
        }

        // PBR 因子
        if (pbr.has("metallicFactor")) {
            data.metallicFactor = (float) pbr.get("metallicFactor").asDouble();
        }
        if (pbr.has("roughnessFactor")) {
            data.roughnessFactor = (float) pbr.get("roughnessFactor").asDouble();
        }
        JsonNode bcf = pbr.path("baseColorFactor");
        if (bcf.isArray() && bcf.size() >= 4) {
            for (int i = 0; i < 4; i++) {
                data.baseColorFactor[i] = (float) bcf.get(i).asDouble();
            }
        }

        // 法线贴图
        if (mat.has("normalTexture")) {
            int texIdx = mat.path("normalTexture").get("index").asInt();
            data.normalTexturePath = resolveTexturePath(root, texIdx, basePath);
        }

        // 自发光贴图
        if (mat.has("emissiveTexture")) {
            int texIdx = mat.path("emissiveTexture").get("index").asInt();
            data.emissiveTexturePath = resolveTexturePath(root, texIdx, basePath);
        }

        // 自发光因子
        JsonNode ef = mat.path("emissiveFactor");
        if (ef.isArray() && ef.size() >= 3) {
            for (int i = 0; i < 3; i++) {
                data.emissiveFactor[i] = (float) ef.get(i).asDouble();
            }
        }

        // KHR_materials_specular 扩展：镜面反射贴图
        JsonNode specularExt = mat.path("extensions").path("KHR_materials_specular");
        if (specularExt.has("specularTexture")) {
            int texIdx = specularExt.path("specularTexture").get("index").asInt();
            data.specularTexturePath = resolveTexturePath(root, texIdx, basePath);
        }

        return data;
    }

    /**
     * 通过 texture 索引找到 image 的 uri，拼接完整路径。
     *
     * @param root     glTF JSON 根节点
     * @param texIdx   texture 索引
     * @param basePath glTF 文件所在目录
     * @return 贴图相对于 assets 根目录的路径
     */
    private static String resolveTexturePath(JsonNode root, int texIdx, String basePath) {
        JsonNode texture = root.path("textures").get(texIdx);
        int sourceIdx = texture.get("source").asInt();
        JsonNode image = root.path("images").get(sourceIdx);
        String uri = image.get("uri").asText();
        return basePath + uri;
    }
}
