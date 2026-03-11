<script setup lang="ts">
/**
 * @file ConfirmDialog.vue
 *
 * @description
 * 确认弹窗组件：符合StarAxis整体UI风格的二次确认对话框喵。
 * 替代原生confirm，提供统一视觉体验。
 */
import { ref, watch } from 'vue'

interface ConfirmOptions {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  danger?: boolean // 危险操作样式（红色强调）
}

const props = defineProps<{
  modelValue: boolean
  options: ConfirmOptions
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()

const isVisible = ref(false)
const isClosing = ref(false)

// 处理显示/隐藏动画喵
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    isClosing.value = false
    isVisible.value = true
  } else {
    closeDialog()
  }
})

function closeDialog() {
  isClosing.value = true
  setTimeout(() => {
    isVisible.value = false
    emit('update:modelValue', false)
  }, 200)
}

function onConfirm() {
  emit('confirm')
  closeDialog()
}

function onCancel() {
  emit('cancel')
  closeDialog()
}

function onOverlayClick(event: MouseEvent) {
  if (event.target === event.currentTarget) {
    onCancel()
  }
}

// 处理键盘事件喵
function onKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    onCancel()
  } else if (event.key === 'Enter') {
    onConfirm()
  }
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div
        v-if="isVisible"
        class="confirm-overlay"
        :class="{ closing: isClosing }"
        @click="onOverlayClick"
        @keydown="onKeyDown"
        tabindex="-1"
      >
        <div class="confirm-dialog" :class="{ 'danger-mode': options.danger }">
          <div class="dialog-header">
            <div class="warning-icon">
              <svg v-if="options.danger" viewBox="0 0 24 24" width="32" height="32">
                <path
                  fill="currentColor"
                  d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 15v-2h2v2h-2zm0-10v6h2V7h-2z"
                />
              </svg>
              <svg v-else viewBox="0 0 24 24" width="32" height="32">
                <path
                  fill="currentColor"
                  d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z"
                />
              </svg>
            </div>
            <h3 class="dialog-title">{{ options.title || '确认操作' }}</h3>
          </div>

          <div class="dialog-body">
            <p class="dialog-message">{{ options.message }}</p>
          </div>

          <div class="dialog-footer">
            <button
              class="sa-btn cancel-btn"
              @click="onCancel"
            >
              {{ options.cancelText || '取消' }}
            </button>
            <button
              class="sa-btn confirm-btn"
              :class="{ danger: options.danger }"
              @click="onConfirm"
            >
              {{ options.confirmText || '确认' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 遮罩层喵 */
.confirm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 20px;
  opacity: 0;
  animation: fadeIn 0.2s ease forwards;
}

.confirm-overlay.closing {
  animation: fadeOut 0.2s ease forwards;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes fadeOut {
  from { opacity: 1; }
  to { opacity: 0; }
}

/* 弹窗主体喵 */
.confirm-dialog {
  background: color-mix(in srgb, var(--panel-bg) 85%, rgba(0, 0, 0, 0.15));
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 24px;
  max-width: 420px;
  width: 100%;
  box-shadow:
    0 0 40px color-mix(in srgb, var(--glow-color) 15%, transparent),
    0 8px 32px rgba(0, 0, 0, 0.4);
  transform: scale(0.9) translateY(20px);
  opacity: 0;
  animation: slideIn 0.25s ease 0.05s forwards;
}

.confirm-overlay.closing .confirm-dialog {
  animation: slideOut 0.2s ease forwards;
}

@keyframes slideIn {
  from {
    transform: scale(0.9) translateY(20px);
    opacity: 0;
  }
  to {
    transform: scale(1) translateY(0);
    opacity: 1;
  }
}

@keyframes slideOut {
  from {
    transform: scale(1) translateY(0);
    opacity: 1;
  }
  to {
    transform: scale(0.9) translateY(20px);
    opacity: 0;
  }
}

/* 危险模式样式喵 */
.confirm-dialog.danger-mode {
  border-color: color-mix(in srgb, var(--danger-color) 60%, var(--border-color));
  box-shadow:
    0 0 40px color-mix(in srgb, var(--danger-color) 20%, transparent),
    0 8px 32px rgba(0, 0, 0, 0.4);
}

/* 头部区域喵 */
.dialog-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 16px;
  text-align: center;
}

.warning-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  border: 2px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12px;
  color: var(--glow-color);
  animation: pulse 2s infinite;
}

.danger-mode .warning-icon {
  background: color-mix(in srgb, var(--danger-color) 15%, transparent);
  border-color: color-mix(in srgb, var(--danger-color) 40%, transparent);
  color: var(--danger-color);
}

@keyframes pulse {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.05); opacity: 0.8; }
}

.dialog-title {
  margin: 0;
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color);
  font-family: 'Orbitron', sans-serif;
}

.danger-mode .dialog-title {
  text-shadow: 0 0 8px var(--danger-color);
}

/* 内容区域喵 */
.dialog-body {
  margin-bottom: 24px;
  text-align: center;
}

.dialog-message {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--text-color);
  white-space: pre-line;
}

/* 底部按钮区域喵 */
.dialog-footer {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.sa-btn {
  padding: 10px 24px;
  border-radius: 10px;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
  color: var(--text-color);
}

.sa-btn:hover {
  background: color-mix(in srgb, var(--sa-bg1) 80%, transparent);
  border-color: var(--glow-color);
  transform: translateY(-1px);
}

.sa-btn:active {
  transform: translateY(0);
}

/* 取消按钮喵 */
.cancel-btn {
  background: transparent;
}

.cancel-btn:hover {
  background: color-mix(in srgb, var(--sa-bg1) 40%, transparent);
}

/* 确认按钮喵 */
.confirm-btn {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  border-color: color-mix(in srgb, var(--glow-color) 60%, transparent);
  color: var(--text-color-hover);
}

.confirm-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 35%, transparent);
  box-shadow: 0 0 16px color-mix(in srgb, var(--glow-color) 40%, transparent);
}

/* 危险确认按钮喵 */
.confirm-btn.danger {
  background: color-mix(in srgb, var(--danger-color) 25%, transparent);
  border-color: color-mix(in srgb, var(--danger-color) 70%, transparent);
  color: var(--text-color-hover);
}

.confirm-btn.danger:hover {
  background: color-mix(in srgb, var(--danger-color) 40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--danger-color) 50%, transparent);
  border-color: var(--danger-color);
}

/* 响应式设计喵 */
@media (max-width: 480px) {
  .confirm-dialog {
    padding: 20px;
    margin: 16px;
  }

  .dialog-title {
    font-size: 1.1rem;
  }

  .dialog-message {
    font-size: 0.9rem;
  }

  .dialog-footer {
    flex-direction: column-reverse;
  }

  .sa-btn {
    width: 100%;
    padding: 12px;
  }
}
</style>
