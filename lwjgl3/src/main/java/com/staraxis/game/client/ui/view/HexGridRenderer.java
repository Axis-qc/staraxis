package com.staraxis.game.client.ui.view;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.staraxis.game.client.world.SectorModel;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.core.coordinate.CameraWorld;
import com.staraxis.game.shared.util.UnitConverter;
import com.staraxis.game.shared.world.HexCoord;

/**
 * 六边形网格渲染器（核心世界渲染 + UI 标签）。
 * 现支持独立 CameraWorld (km) → pixel 投影。
 */
public class HexGridRenderer {

    // ---------------- 渲染依赖 ----------------
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch textBatch;
    private final OrthographicCamera uiCamera;

    private BitmapFont font;
    private CameraWorld camWorld; // 摄像机世界中心 (km)

    // ---------------- 配置 ----------------
    private float hexRadiusPx = 50f; // 旧抽象网格时的“像素半径”
    private boolean showCoordinates = true;

    // UI 标签过滤
    private float coordRadiusPx = 220f;
    private int maxCoordLabels = 40;

    // ---------- 星区物理尺寸 ----------
    private static final double SECTOR_RADIUS_LY = 0.5;
    private static final double LY_TO_KM = 9_460_730_472_580.8;
    private static final double MIN_KM_PER_PIXEL_FOR_SECTORS = 1e4;

    public HexGridRenderer(CameraWorld camWorld) {
        this.camWorld = camWorld;
        this.shapeRenderer = new ShapeRenderer();
        this.textBatch = new SpriteBatch();
        this.uiCamera = new OrthographicCamera();
        this.uiCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.uiCamera.update();
    }

    /** 兼容旧调用：世界渲染用 ShapeRenderer，投影矩阵由 Screen 传入。 */
    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    public void dispose() {
        shapeRenderer.dispose();
        textBatch.dispose();
        // font 由 Skin 管理，不在此 dispose
    }

    public void setFont(BitmapFont font) { this.font = font; }

    public void resizeUiViewport(int w, int h) { uiCamera.setToOrtho(false, w, h); uiCamera.update(); }

    public float getHexRadius() { return hexRadiusPx; }

    // ---------------- 主渲染（世界层） ----------------
    public void render(UniverseModel universe, HexCoord highlighted, double kmPerPixel, Camera camera) {
        if (universe == null || camWorld == null) return;
        if (kmPerPixel < MIN_KM_PER_PIXEL_FOR_SECTORS) return;

        double sectorRadiusKm = SECTOR_RADIUS_LY * LY_TO_KM;
        float radiusPx = (float)(sectorRadiusKm / kmPerPixel);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        for (SectorModel s : universe.getSectors().values()) {
            double cxKm = UnitConverter.lightYearsToKm(s.getWorldPositionXLy());
            double cyKm = UnitConverter.lightYearsToKm(s.getWorldPositionYLy());
            float px = (float)((cxKm - camWorld.getXKm()) / kmPerPixel);
            float py = (float)((cyKm - camWorld.getYKm()) / kmPerPixel);
            if (!camera.frustum.boundsInFrustum(px, py, 0, radiusPx*1.2f, radiusPx*1.2f,0)) continue;
            shapeRenderer.setColor(Color.GRAY);
            drawHex(px, py, radiusPx);
        }
        shapeRenderer.end();
    }

    private void drawHex(float cx,float cy,float r){
        float[] v=new float[12];
        for(int i=0;i<6;i++){double a=Math.toRadians(60*i-30);v[i*2]=cx+(float)(r*Math.cos(a));v[i*2+1]=cy+(float)(r*Math.sin(a));}
        shapeRenderer.polygon(v);
    }

    // ---------------- UI 标签 ----------------
    private final Vector3 tmp = new Vector3();
    public void renderSectorCenterCoordinatesUi(UniverseModel universe, Camera worldCam){
        if(!showCoordinates||font==null||camWorld==null) return;
        float mx=Gdx.input.getX();float myScr=Gdx.graphics.getHeight()-Gdx.input.getY();float r2=coordRadiusPx*coordRadiusPx;
        textBatch.setProjectionMatrix(uiCamera.combined);textBatch.begin();font.setColor(Color.WHITE);
        int drawn=0;double kmPerPixel=((OrthographicCamera)worldCam).zoom;
        for(SectorModel s:universe.getSectors().values()){
            if(drawn>=maxCoordLabels) break;
            double xKm=UnitConverter.lightYearsToKm(s.getWorldPositionXLy());
            double yKm=UnitConverter.lightYearsToKm(s.getWorldPositionYLy());
            float px=(float)((xKm-camWorld.getXKm())/kmPerPixel);
            float py=(float)((yKm-camWorld.getYKm())/kmPerPixel);
            tmp.set(px,py,0);worldCam.project(tmp);
            float dx=tmp.x-mx,dy=tmp.y-myScr;if(dx*dx+dy*dy>r2) continue;
            font.draw(textBatch,String.format("(%.2f,%.2f)",s.getWorldPositionXLy(),s.getWorldPositionYLy()),tmp.x+8,tmp.y+12);
            drawn++;}
        textBatch.end();
    }

    public void renderMouseWorldCoordUi(Camera worldCam){
        if(font==null||camWorld==null) return;
        float mx=Gdx.input.getX();float myScr=Gdx.graphics.getHeight()-Gdx.input.getY();
        double kmPerPixel=((OrthographicCamera)worldCam).zoom;
        tmp.set(mx,Gdx.input.getY(),0);worldCam.unproject(tmp);
        double xKm=tmp.x*kmPerPixel+camWorld.getXKm();
        double yKm=tmp.y*kmPerPixel+camWorld.getYKm();
        textBatch.setProjectionMatrix(uiCamera.combined);textBatch.begin();font.setColor(Color.CYAN);
        font.draw(textBatch,String.format("(%.3f,%.3f) ly",UnitConverter.kmToLightYears(xKm),UnitConverter.kmToLightYears(yKm)),mx+12,myScr+18);
        textBatch.end();
    }
}
