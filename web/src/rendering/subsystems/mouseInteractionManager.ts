/**
 * @file mouseInteractionManager.ts
 * @description 鍏叡榧犳爣浜や簰绠＄悊鍣?- 缁熶竴妫€鏌ラ紶鏍囦綅缃紝鍒嗗彂浜嬩欢缁欏懡涓厓绱犲柕
 * @important_notes
 * - 鐩戝惉 canvas 鎸囬拡浜嬩欢锛岃褰曢紶鏍囩姸鎬佸柕
 * - 姣忓抚 update 涓鏌ユ敞鍐岀殑 Interactable锛屽懡涓垯鍒嗗彂浜嬩欢鍠?
 * - 鎸夋敞鍐屼紭鍏堢骇妫€鏌ワ紝鍏堟敞鍐岀殑浼樺厛绾ф洿楂樺柕
 */

/**
 * 鍙氦浜掑厓绱犳帴鍙ｅ柕
 * 瀹炵幇姝ゆ帴鍙ｅ嵆鍙帴鏀堕紶鏍囦氦浜掍簨浠跺柕
 */
export interface Interactable {
    /** 灞忓箷鐭╁舰鍛戒腑妫€娴嬶紙canvas 鍍忕礌鍧愭爣锛夊柕 */
    hitTest(canvasX: number, canvasY: number): boolean
    /** 榧犳爣鎸変笅鍥炶皟鍠?*/
    onPointerDown(canvasX: number, canvasY: number): void
    /** 榧犳爣绉诲姩鍥炶皟锛堝甫澧為噺锛夊柕 */
    onPointerMove(canvasX: number, canvasY: number, dx: number, dy: number): void
    /** 榧犳爣閲婃斁鍥炶皟鍠?*/
    onPointerUp(): void
}

/**
 * 榧犳爣浜や簰绠＄悊鍣ㄥ柕
 */
export class MouseInteractionManager {
    private canvas: HTMLCanvasElement | null = null

    // 榧犳爣鐘舵€佸柕
    private canvasX = 0
    private canvasY = 0
    private isDown = false
    private justUp = false

    // 娉ㄥ唽鐨勫彲浜や簰鍏冪礌锛堟湁搴忥紝鍏堟敞鍐屼紭鍏堢骇鏇撮珮锛夊柕
    private readonly interactables: Interactable[] = []
    // 褰撳墠娲昏穬鐨?interactable锛堟鍦ㄦ帴鏀朵簨浠剁殑閭ｄ釜锛夊柕
    private activeTarget: Interactable | null = null

    // 浜嬩欢澶勭悊鍣ㄥ紩鐢ㄥ柕
    private readonly onPointerDownBound = this.handlePointerDown.bind(this)
    private readonly onPointerMoveBound = this.handlePointerMove.bind(this)
    private readonly onPointerUpBound = this.handlePointerUp.bind(this)

    /**
     * 缁戝畾 canvas锛屽紑濮嬬洃鍚簨浠跺柕
     */
    bindCanvas(canvas: HTMLCanvasElement): void {
        this.canvas = canvas
        canvas.addEventListener('pointerdown', this.onPointerDownBound)
        canvas.addEventListener('pointermove', this.onPointerMoveBound)
        canvas.addEventListener('pointerup', this.onPointerUpBound)
        canvas.addEventListener('pointercancel', this.onPointerUpBound)
    }

    /**
     * 娉ㄥ唽鍙氦浜掑厓绱犲柕
     * 鍏堟敞鍐岀殑浼樺厛绾ф洿楂橈紙鍏堟鏌ワ級鍠?
     */
    register(interactable: Interactable): void {
        this.interactables.push(interactable)
    }

    /**
     * 娉ㄩ攢鍙氦浜掑厓绱犲柕
     */
    unregister(interactable: Interactable): void {
        const idx = this.interactables.indexOf(interactable)
        if (idx >= 0) this.interactables.splice(idx, 1)
        if (this.activeTarget === interactable) this.activeTarget = null
    }

    /**
     * 姣忓抚璋冪敤锛氬鐞嗛噴鏀句簨浠跺柕
     */
    update(): void {
        // 榧犳爣閲婃斁锛氶€氱煡娲昏穬鐩爣鍠?
        if (this.justUp && this.activeTarget) {
            this.activeTarget.onPointerUp()
            this.activeTarget = null
        }

        // 閲嶇疆鍗曞抚鏍囪鍠?
        this.justUp = false
    }

    /**
     * 閲婃斁鎵€鏈夎祫婧愬柕
     */
    dispose(): void {
        if (this.canvas) {
            this.canvas.removeEventListener('pointerdown', this.onPointerDownBound)
            this.canvas.removeEventListener('pointermove', this.onPointerMoveBound)
            this.canvas.removeEventListener('pointerup', this.onPointerUpBound)
            this.canvas.removeEventListener('pointercancel', this.onPointerUpBound)
            this.canvas = null
        }
        this.activeTarget = null
        this.interactables.length = 0
    }

    // 鈹€鈹€ 浜嬩欢澶勭悊 鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鍠?

    private getCanvasPos(e: PointerEvent): { x: number; y: number } | null {
        const rect = this.canvas?.getBoundingClientRect()
        if (!rect) return null
        return { x: e.clientX - rect.left, y: e.clientY - rect.top }
    }

    private handlePointerDown(e: PointerEvent): void {
        if (e.button !== 0) return
        const pos = this.getCanvasPos(e)
        if (!pos) return
        this.canvasX = pos.x
        this.canvasY = pos.y
        this.isDown = true

        // 绔嬪嵆鍛戒腑妫€娴嬪苟璁剧疆 activeTarget锛屼笉绛変笅涓€甯у柕
        for (const target of this.interactables) {
            if (target.hitTest(pos.x, pos.y)) {
                this.activeTarget = target
                target.onPointerDown(pos.x, pos.y)
                // 闃绘鍐掓场鍒?Vue 瀹瑰櫒灞傦紙閬垮厤瑙﹀彂妗嗛€夛級鍠?
                e.stopPropagation()
                return
            }
        }
    }

    private handlePointerMove(e: PointerEvent): void {
        const pos = this.getCanvasPos(e)
        if (!pos) return
        const dx = pos.x - this.canvasX
        const dy = pos.y - this.canvasY
        this.canvasX = pos.x
        this.canvasY = pos.y

        // 鎷栧姩涓細瀹炴椂杞彂缁欐椿璺冪洰鏍囧柕
        if (this.activeTarget && this.isDown) {
            this.activeTarget.onPointerMove(pos.x, pos.y, dx, dy)
        }
    }

    private handlePointerUp(e: PointerEvent): void {
        if (e.button !== 0) return
        this.isDown = false
        this.justUp = true
    }
}
