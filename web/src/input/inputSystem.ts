/**
 * @file inputSystem.ts
 *
 * @description
 * 统一输入控制系统 - 集中管理键盘和鼠标输入，支持键位自定义。
 *
 * 作用：
 * - 统一管理所有输入设备（键盘、鼠标、滚轮）的事件监听。
 * - 提供可配置的键位映射系统，支持运行时修改键位。
 * - 将原始输入事件转换为游戏内动作（Action）。
 * - 支持输入组合键（如 Ctrl+点击）和修饰键检测。
 * - 提供输入状态查询（某键是否按下、鼠标位置等）。
 *
 * @usage
 * - 在 WorldRenderManager 或其他需要输入的组件中创建 InputSystem 实例。
 * - 通过 `bindAction(action, key)` 配置键位映射。
 * - 通过 `onAction(action, callback)` 订阅特定动作的事件。
 * - 通过 `setKeyMap()` 和 `loadKeyMap()` 保存/加载自定义键位。
 *
 * @provides
 * - **InputSystem 类**: 主控制系统。
 * - **Action 类型**: 预定义的游戏动作（MOVE_CAMERA, ZOOM_IN 等）。
 * - **KeyBinding 类型**: 键位绑定配置。
 * - **键位持久化**: 支持导出/导入键位配置（JSON）。
 *
 * @important_notes
 * - 所有原始事件默认阻止默认行为（preventDefault）。
 * - 键位区分大小写，使用 event.code（物理键位）而非 event.key（字符）。
 * - 鼠标位置以像素为单位，原点在左上角。
 * - 滚轮事件已标准化（deltaY 向上为正，向下为负）。
 */

/**
 * 游戏动作类型
 * 定义所有可由输入触发的游戏内动作
 */
export type InputAction =
    // 相机控制
    | 'CAMERA_PAN'           // 平移相机（中键拖拽）
    | 'CAMERA_PAN_START'     // 开始平移
    | 'CAMERA_ZOOM_IN'       // 放大
    | 'CAMERA_ZOOM_OUT'      // 缩小
    | 'CAMERA_ZOOM_RESET'    // 重置缩放
    | 'CAMERA_PAN_UP'        // 向上平移
    | 'CAMERA_PAN_DOWN'      // 向下平移
    | 'CAMERA_PAN_LEFT'      // 向左平移
    | 'CAMERA_PAN_RIGHT'     // 向右平移
    // 选择控制
    | 'SELECT'               // 选择实体（左键点击）
    | 'MULTI_SELECT'         // 多选（Shift+点击）
    | 'DESELECT'             // 取消选择（Esc）
    // 界面控制
    | 'TOGGLE_GRID'          // 切换网格显示
    | 'TOGGLE_ORBIT'         // 切换轨道显示
    | 'OPEN_MENU'            // 打开菜单
    | 'CLOSE_MENU'           // 关闭菜单/返回
    // 时间控制
    | 'PAUSE'                // 暂停/继续
    | 'SPEED_UP'             // 加速时间
    | 'SPEED_DOWN'           // 减速时间
    // 调试
    | 'TOGGLE_DEBUG'         // 切换调试信息

/**
 * 修饰键状态
 */
export type ModifierKeys = {
    ctrl: boolean
    shift: boolean
    alt: boolean
    meta: boolean
}

/**
 * 单个键位绑定配置
 */
export type KeyBinding = {
    /** 动作名称 */
    action: InputAction
    /** 键盘按键代码 (如 'KeyW', 'ArrowUp', 'Space') */
    key?: string
    /** 鼠标按钮 (0=左键, 1=中键, 2=右键) */
    mouseButton?: number
    /** 滚轮方向 ('up' | 'down') */
    wheel?: 'up' | 'down'
    /** 需要的修饰键 */
    modifiers?: Partial<ModifierKeys>
}

/**
 * 输入事件数据
 */
export type InputEventData = {
    /** 触发的动作 */
    action: InputAction
    /** 原始 DOM 事件 */
    originalEvent: Event
    /** 修饰键状态 */
    modifiers: ModifierKeys
    /** 鼠标位置（如果是鼠标事件） */
    mousePos?: { x: number; y: number }
    /** 鼠标Delta（如果是滚轮事件） */
    delta?: { x: number; y: number }
}

/**
 * 输入状态快照
 */
export type InputState = {
    /** 当前按下的按键集合 */
    pressedKeys: Set<string>
    /** 当前按下的鼠标按钮集合 */
    pressedMouseButtons: Set<number>
    /** 当前鼠标位置 */
    mousePosition: { x: number; y: number }
    /** 当前修饰键状态 */
    modifiers: ModifierKeys
}

/**
 * 动作回调函数类型
 */
export type ActionCallback = (data: InputEventData) => void

/**
 * 默认键位映射
 */
export const DEFAULT_KEY_MAP: KeyBinding[] = [
    // 相机控制 - 鼠标
    { action: 'CAMERA_PAN', mouseButton: 1 },
    { action: 'CAMERA_PAN_START', mouseButton: 1 },
    { action: 'SELECT', mouseButton: 0 },
    { action: 'MULTI_SELECT', mouseButton: 0, modifiers: { shift: true } },
    
    // 相机控制 - 键盘（方向键 + WASD）
    { action: 'CAMERA_PAN_UP', key: 'ArrowUp' },
    { action: 'CAMERA_PAN_DOWN', key: 'ArrowDown' },
    { action: 'CAMERA_PAN_LEFT', key: 'ArrowLeft' },
    { action: 'CAMERA_PAN_RIGHT', key: 'ArrowRight' },
    { action: 'CAMERA_PAN_UP', key: 'KeyW' },
    { action: 'CAMERA_PAN_DOWN', key: 'KeyS' },
    { action: 'CAMERA_PAN_LEFT', key: 'KeyA' },
    { action: 'CAMERA_PAN_RIGHT', key: 'KeyD' },
    { action: 'CAMERA_ZOOM_RESET', key: 'KeyR' },
    
    // 界面控制
    { action: 'DESELECT', key: 'Escape' },
    { action: 'OPEN_MENU', key: 'KeyM' },
    { action: 'CLOSE_MENU', key: 'Escape' },
    { action: 'TOGGLE_GRID', key: 'KeyG' },
    { action: 'TOGGLE_ORBIT', key: 'KeyO' },
    
    // 时间控制
    { action: 'PAUSE', key: 'Space' },
    { action: 'SPEED_UP', key: 'Equal', modifiers: { shift: true } },
    { action: 'SPEED_DOWN', key: 'Minus' },
    
    // 调试
    { action: 'TOGGLE_DEBUG', key: 'F12' },
]

/**
 * 输入控制系统
 */
export class InputSystem {
    private container: HTMLElement
    private keyMap: Map<string, KeyBinding[]> = new Map()
    private actionListeners: Map<InputAction, Set<ActionCallback>> = new Map()
    private pressedKeys: Set<string> = new Set()
    private pressedMouseButtons: Set<number> = new Set()
    private mousePosition: { x: number; y: number } = { x: 0, y: 0 }
    private isEnabled: boolean = true

    constructor(container: HTMLElement) {
        this.container = container
        this.loadKeyMap(DEFAULT_KEY_MAP)
        this.attachListeners()
    }

    /**
     * 加载键位映射配置
     */
    loadKeyMap(bindings: KeyBinding[]): void {
        this.keyMap.clear()
        for (const binding of bindings) {
            const key = this.getBindingKey(binding)
            if (!this.keyMap.has(key)) {
                this.keyMap.set(key, [])
            }
            this.keyMap.get(key)!.push(binding)
        }
    }

    /**
     * 导出当前键位配置
     */
    exportKeyMap(): KeyBinding[] {
        const result: KeyBinding[] = []
        for (const bindings of this.keyMap.values()) {
            result.push(...bindings)
        }
        return result
    }

    /**
     * 绑定单个动作到按键
     */
    bindAction(action: InputAction, key: string, modifiers?: Partial<ModifierKeys>): void {
        const binding: KeyBinding = { action, key, modifiers }
        const mapKey = this.getBindingKey(binding)
        
        // 移除同一动作的旧绑定
        this.unbindAction(action)
        
        if (!this.keyMap.has(mapKey)) {
            this.keyMap.set(mapKey, [])
        }
        this.keyMap.get(mapKey)!.push(binding)
    }

    /**
     * 绑定鼠标动作
     */
    bindMouseAction(action: InputAction, button: number, modifiers?: Partial<ModifierKeys>): void {
        const binding: KeyBinding = { action, mouseButton: button, modifiers }
        const mapKey = this.getBindingKey(binding)
        
        this.unbindAction(action)
        
        if (!this.keyMap.has(mapKey)) {
            this.keyMap.set(mapKey, [])
        }
        this.keyMap.get(mapKey)!.push(binding)
    }

    /**
     * 解绑动作的所有按键
     */
    unbindAction(action: InputAction): void {
        for (const [key, bindings] of this.keyMap.entries()) {
            const filtered = bindings.filter(b => b.action !== action)
            if (filtered.length === 0) {
                this.keyMap.delete(key)
            } else {
                this.keyMap.set(key, filtered)
            }
        }
    }

    /**
     * 订阅动作事件
     */
    onAction(action: InputAction, callback: ActionCallback): () => void {
        if (!this.actionListeners.has(action)) {
            this.actionListeners.set(action, new Set())
        }
        this.actionListeners.get(action)!.add(callback)
        
        // 返回取消订阅函数
        return () => {
            this.actionListeners.get(action)?.delete(callback)
        }
    }

    /**
     * 触发动作（可用于程序化触发）
     */
    triggerAction(action: InputAction, data?: Partial<InputEventData>): void {
        const listeners = this.actionListeners.get(action)
        if (!listeners || listeners.size === 0) return

        const eventData: InputEventData = {
            action,
            originalEvent: data?.originalEvent || new Event('synthetic'),
            modifiers: data?.modifiers || this.getModifiers(),
            mousePos: data?.mousePos || this.mousePosition,
            delta: data?.delta,
        }

        listeners.forEach(cb => {
            try {
                cb(eventData)
            } catch (e) {
                console.error(`Action handler error for ${action}:`, e)
            }
        })
    }

    /**
     * 获取当前输入状态
     */
    getState(): InputState {
        return {
            pressedKeys: new Set(this.pressedKeys),
            pressedMouseButtons: new Set(this.pressedMouseButtons),
            mousePosition: { ...this.mousePosition },
            modifiers: this.getModifiers(),
        }
    }

    /**
     * 检查某键是否按下
     */
    isKeyPressed(key: string): boolean {
        return this.pressedKeys.has(key)
    }

    /**
     * 检查鼠标按钮是否按下
     */
    isMouseButtonPressed(button: number): boolean {
        return this.pressedMouseButtons.has(button)
    }

    /**
     * 启用/禁用输入系统
     */
    setEnabled(enabled: boolean): void {
        this.isEnabled = enabled
    }

    /**
     * 销毁系统，清理事件监听
     */
    dispose(): void {
        // 移除 window 上的键盘事件
        window.removeEventListener('keydown', this.handleKeyDown)
        window.removeEventListener('keyup', this.handleKeyUp)
        window.removeEventListener('blur', this.handleBlur)
        
        // 移除容器上的鼠标事件
        this.container.removeEventListener('mousedown', this.handleMouseDown)
        this.container.removeEventListener('mouseup', this.handleMouseUp)
        this.container.removeEventListener('mousemove', this.handleMouseMove)
        this.container.removeEventListener('wheel', this.handleWheel)
        this.container.removeEventListener('contextmenu', this.handleContextMenu)
        
        this.keyMap.clear()
        this.actionListeners.clear()
        this.pressedKeys.clear()
        this.pressedMouseButtons.clear()
    }

    // ============ 私有方法 ============

    private attachListeners(): void {
        // 键盘事件 - 使用 window 以确保无论焦点在哪都能接收
        window.addEventListener('keydown', this.handleKeyDown)
        window.addEventListener('keyup', this.handleKeyUp)
        
        // 鼠标事件 - 在容器上监听
        this.container.addEventListener('mousedown', this.handleMouseDown)
        this.container.addEventListener('mouseup', this.handleMouseUp)
        this.container.addEventListener('mousemove', this.handleMouseMove)
        this.container.addEventListener('wheel', this.handleWheel, { passive: false })
        this.container.addEventListener('contextmenu', this.handleContextMenu)
        
        // 窗口失去焦点时清理按键状态
        window.addEventListener('blur', this.handleBlur)
    }

    private handleKeyDown = (e: KeyboardEvent): void => {
        if (!this.isEnabled) return
        
        this.pressedKeys.add(e.code)
        
        const bindingKey = this.getKeyBindingKey(e.code, this.extractModifiers(e))
        this.processBinding(bindingKey, e)
    }

    private handleKeyUp = (e: KeyboardEvent): void => {
        this.pressedKeys.delete(e.code)
    }

    private handleMouseDown = (e: MouseEvent): void => {
        if (!this.isEnabled) return
        
        this.pressedMouseButtons.add(e.button)
        this.updateMousePosition(e)
        
        const bindingKey = this.getMouseBindingKey(e.button, this.extractModifiers(e))
        this.processBinding(bindingKey, e)
    }

    private handleMouseUp = (e: MouseEvent): void => {
        this.pressedMouseButtons.delete(e.button)
    }

    private handleMouseMove = (e: MouseEvent): void => {
        this.updateMousePosition(e)
    }

    private handleWheel = (e: WheelEvent): void => {
        if (!this.isEnabled) return
        
        e.preventDefault()
        
        const direction = e.deltaY < 0 ? 'up' : 'down'
        const action = direction === 'up' ? 'CAMERA_ZOOM_IN' : 'CAMERA_ZOOM_OUT'
        
        this.triggerAction(action, {
            originalEvent: e,
            delta: { x: e.deltaX, y: e.deltaY },
        })
    }

    private handleContextMenu = (e: MouseEvent): void => {
        e.preventDefault()
    }

    private handleBlur = (): void => {
        this.pressedKeys.clear()
        this.pressedMouseButtons.clear()
    }

    private updateMousePosition(e: MouseEvent): void {
        const rect = this.container.getBoundingClientRect()
        this.mousePosition = {
            x: e.clientX - rect.left,
            y: e.clientY - rect.top,
        }
    }

    private extractModifiers(e: MouseEvent | KeyboardEvent): ModifierKeys {
        return {
            ctrl: e.ctrlKey,
            shift: e.shiftKey,
            alt: e.altKey,
            meta: e.metaKey,
        }
    }

    private getModifiers(): ModifierKeys {
        // 从当前按键状态推断修饰键
        return {
            ctrl: this.pressedKeys.has('ControlLeft') || this.pressedKeys.has('ControlRight'),
            shift: this.pressedKeys.has('ShiftLeft') || this.pressedKeys.has('ShiftRight'),
            alt: this.pressedKeys.has('AltLeft') || this.pressedKeys.has('AltRight'),
            meta: this.pressedKeys.has('MetaLeft') || this.pressedKeys.has('MetaRight'),
        }
    }

    private getBindingKey(binding: KeyBinding): string {
        if (binding.key) {
            return this.getKeyBindingKey(binding.key, binding.modifiers)
        }
        if (binding.mouseButton !== undefined) {
            return this.getMouseBindingKey(binding.mouseButton, binding.modifiers)
        }
        if (binding.wheel) {
            return `wheel:${binding.wheel}`
        }
        return ''
    }

    private getKeyBindingKey(key: string, modifiers?: Partial<ModifierKeys>): string {
        const mods = this.serializeModifiers(modifiers)
        return `key:${mods}:${key}`
    }

    private getMouseBindingKey(button: number, modifiers?: Partial<ModifierKeys>): string {
        const mods = this.serializeModifiers(modifiers)
        return `mouse:${mods}:${button}`
    }

    private serializeModifiers(mods?: Partial<ModifierKeys>): string {
        if (!mods) return ''
        const parts: string[] = []
        if (mods.ctrl) parts.push('ctrl')
        if (mods.shift) parts.push('shift')
        if (mods.alt) parts.push('alt')
        if (mods.meta) parts.push('meta')
        return parts.join('+')
    }

    private processBinding(bindingKey: string, originalEvent: Event): void {
        const bindings = this.keyMap.get(bindingKey)
        if (!bindings || bindings.length === 0) return

        originalEvent.preventDefault()

        for (const binding of bindings) {
            this.triggerAction(binding.action, {
                originalEvent,
                mousePos: this.mousePosition,
            })
        }
    }
}

/**
 * 创建输入系统的工厂函数
 */
export function createInputSystem(container: HTMLElement): InputSystem {
    return new InputSystem(container)
}
