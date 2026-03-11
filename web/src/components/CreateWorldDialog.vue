<script setup lang="ts">
/**
 * @file CreateWorldDialog.vue
 *
 * @description
 * 创建世界弹窗组件：提供完整的世界创建配置界面喵。
 * 独立弹窗设计，为未来扩展更多选项预留空间。
 */
import { ref, watch } from 'vue'
import type { TickPolicy } from '../net/worldSavesApi'

export interface CreateWorldForm {
  worldName: string
  worldRadius: number
  worldSeed: string
  tickPolicy: TickPolicy
  spawnMode: 'manual' | 'random'
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'create', form: CreateWorldForm): void
  (e: 'cancel'): void
}>()

const isVisible = ref(false)
const isClosing = ref(false)

const form = ref<CreateWorldForm>({
  worldName: '',
  worldRadius: 12,
  worldSeed: '',
  tickPolicy: 'RUN_WHEN_ONLINE',
  spawnMode: 'manual',
})

// 表单验证错误喵
const errors = ref<Record<string, string>>({})

// 处理显示/隐藏动画喵
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    isClosing.value = false
    isVisible.value = true
    resetForm()
  } else {
    closeDialog()
  }
})

function resetForm() {
  form.value = {
    worldName: '',
    worldRadius: 12,
    worldSeed: '',
    tickPolicy: 'RUN_WHEN_ONLINE',
    spawnMode: 'manual',
  }
  errors.value = {}
}

function closeDialog() {
  isClosing.value = true
  setTimeout(() => {
    isVisible.value = false
    emit('update:modelValue', false)
  }, 200)
}

function validateForm(): boolean {
  errors.value = {}

  if (!form.value.worldName.trim()) {
    errors.value.worldName = '请输入世界名称'
  }

  if (form.value.worldRadius < 1 || form.value.worldRadius > 512) {
    errors.value.worldRadius = '半径范围 1-512'
  }

  return Object.keys(errors.value).length === 0
}

function onConfirm() {
  if (!validateForm()) {
    return
  }
  emit('create', { ...form.value })
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

function generateRandomSeed() {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let seed = ''
  for (let i = 0; i < 8; i++) {
    seed += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  form.value.worldSeed = seed
}
</script>

<template>
  <Teleport to="body">
    <Transition name="dialog">
      <div
        v-if="isVisible"
        class="create-world-overlay"
        :class="{ closing: isClosing }"
        @click="onOverlayClick"
      >
        <div class="create-world-dialog">
          <!-- 头部喵 -->
          <div class="dialog-header">
            <div class="header-icon">
              <svg viewBox="0 0 24 24" width="28" height="28">
                <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z"/>
              </svg>
            </div>
            <h2 class="dialog-title">创建新世界</h2>
            <p class="dialog-subtitle">配置你的世界参数，开启星际征程喵</p>
          </div>

          <!-- 表单内容喵 -->
          <div class="dialog-body">
            <!-- 世界名称喵 -->
            <div class="form-section">
              <label class="form-label">
                <span class="label-text">世界名称</span>
                <span class="label-required">*</span>
              </label>
              <input
                v-model="form.worldName"
                class="form-input"
                :class="{ 'has-error': errors.worldName }"
                placeholder="为你的世界取一个名字"
                maxlength="32"
              />
              <span v-if="errors.worldName" class="error-text">{{ errors.worldName }}</span>
            </div>

            <!-- 种子设置喵 -->
            <div class="form-section">
              <label class="form-label">
                <span class="label-text">世界种子</span>
                <span class="label-hint">（可选，影响星系生成）</span>
              </label>
              <div class="seed-input-row">
                <input
                  v-model="form.worldSeed"
                  class="form-input seed-input"
                  placeholder="留空则随机生成"
                  maxlength="16"
                />
                <button class="sa-btn mini random-btn" @click="generateRandomSeed" title="随机生成种子">
                  <svg viewBox="0 0 24 24" width="16" height="16">
                    <path fill="currentColor" d="M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z"/>
                  </svg>
                  随机
                </button>
              </div>
            </div>

            <!-- 世界半径喵 -->
            <div class="form-section">
              <label class="form-label">
                <span class="label-text">星系半径</span>
                <span class="label-hint">（{{ form.worldRadius }} 光年，影响星系数量）</span>
              </label>
              <div class="radius-slider-row">
                <input
                  v-model.number="form.worldRadius"
                  type="range"
                  class="radius-slider"
                  min="4"
                  max="64"
                  step="2"
                />
                <input
                  v-model.number="form.worldRadius"
                  type="number"
                  class="form-input radius-number"
                  :class="{ 'has-error': errors.worldRadius }"
                  min="1"
                  max="512"
                />
              </div>
              <span v-if="errors.worldRadius" class="error-text">{{ errors.worldRadius }}</span>
            </div>

            <!-- 两列布局喵 -->
            <div class="form-row-two">
              <!-- 时间推进策略喵 -->
              <div class="form-section">
                <label class="form-label">
                  <span class="label-text">时间推进</span>
                </label>
                <div class="option-cards">
                  <div
                    class="option-card"
                    :class="{ active: form.tickPolicy === 'RUN_WHEN_ONLINE' }"
                    @click="form.tickPolicy = 'RUN_WHEN_ONLINE'"
                  >
                    <div class="card-icon">
                      <svg viewBox="0 0 24 24" width="24" height="24">
                        <path fill="currentColor" d="M9 16h2V8H9v8zm3-14C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm1-4h2V8h-2v8z"/>
                      </svg>
                    </div>
                    <div class="card-content">
                      <div class="card-title">在线推进</div>
                      <div class="card-desc">有玩家在线时才推进时间</div>
                    </div>
                  </div>
                  <div
                    class="option-card"
                    :class="{ active: form.tickPolicy === 'ALWAYS_RUN' }"
                    @click="form.tickPolicy = 'ALWAYS_RUN'"
                  >
                    <div class="card-icon">
                      <svg viewBox="0 0 24 24" width="24" height="24">
                        <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5v-9l6 4.5-6 4.5z"/>
                      </svg>
                    </div>
                    <div class="card-content">
                      <div class="card-title">持续推进</div>
                      <div class="card-desc">无视在线状态持续推进</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 出生点策略喵 -->
              <div class="form-section">
                <label class="form-label">
                  <span class="label-text">出生点设置</span>
                </label>
                <div class="option-cards">
                  <div
                    class="option-card"
                    :class="{ active: form.spawnMode === 'manual' }"
                    @click="form.spawnMode = 'manual'"
                  >
                    <div class="card-icon">
                      <svg viewBox="0 0 24 24" width="24" height="24">
                        <path fill="currentColor" d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
                      </svg>
                    </div>
                    <div class="card-content">
                      <div class="card-title">手动选择</div>
                      <div class="card-desc">进入游戏后选择出生星系</div>
                    </div>
                  </div>
                  <div
                    class="option-card"
                    :class="{ active: form.spawnMode === 'random' }"
                    @click="form.spawnMode = 'random'"
                  >
                    <div class="card-icon">
                      <svg viewBox="0 0 24 24" width="24" height="24">
                        <path fill="currentColor" d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/>
                      </svg>
                    </div>
                    <div class="card-content">
                      <div class="card-title">随机分配</div>
                      <div class="card-desc">创建时随机分配出生点</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 底部按钮喵 -->
          <div class="dialog-footer">
            <button class="sa-btn cancel-btn" @click="onCancel">
              取消
            </button>
            <button class="sa-btn create-btn" @click="onConfirm">
              <svg viewBox="0 0 24 24" width="18" height="18">
                <path fill="currentColor" d="M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"/>
              </svg>
              创建世界
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* 遮罩层喵 */
.create-world-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9998;
  padding: 20px;
  opacity: 0;
  animation: fadeIn 0.25s ease forwards;
}

.create-world-overlay.closing {
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
.create-world-dialog {
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--panel-bg) 90%, rgba(0, 0, 0, 0.1)),
    color-mix(in srgb, var(--panel-bg) 70%, rgba(0, 0, 0, 0.3))
  );
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, var(--border-color));
  border-radius: 20px;
  width: 100%;
  max-width: 640px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow:
    0 0 60px color-mix(in srgb, var(--glow-color) 15%, transparent),
    0 12px 48px rgba(0, 0, 0, 0.5);
  transform: scale(0.95) translateY(20px);
  opacity: 0;
  animation: slideIn 0.3s ease 0.05s forwards;
}

.create-world-overlay.closing .create-world-dialog {
  animation: slideOut 0.2s ease forwards;
}

@keyframes slideIn {
  from {
    transform: scale(0.95) translateY(20px);
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
    transform: scale(0.95) translateY(20px);
    opacity: 0;
  }
}

/* 头部喵 */
.dialog-header {
  text-align: center;
  padding: 28px 24px 20px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 50%, transparent);
}

.header-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 25%, transparent),
    color-mix(in srgb, var(--glow-color) 10%, transparent)
  );
  border: 2px solid color-mix(in srgb, var(--glow-color) 50%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
  color: var(--glow-color);
  box-shadow: 0 0 24px color-mix(in srgb, var(--glow-color) 30%, transparent);
}

.dialog-title {
  margin: 0 0 8px;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 12px var(--glow-color);
  font-family: 'Orbitron', sans-serif;
}

.dialog-subtitle {
  margin: 0;
  font-size: 0.9rem;
  color: var(--sa-muted);
}

/* 表单内容喵 */
.dialog-body {
  padding: 24px;
}

.form-section {
  margin-bottom: 24px;
}

.form-section:last-child {
  margin-bottom: 0;
}

.form-label {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-color);
}

.label-required {
  color: var(--danger-color);
}

.label-hint {
  font-size: 0.8rem;
  font-weight: 400;
  color: var(--sa-muted);
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: color-mix(in srgb, var(--sa-bg1) 40%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.95rem;
  transition: all 0.2s ease;
}

.form-input:focus {
  outline: none;
  border-color: var(--glow-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--glow-color) 15%, transparent);
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
}

.form-input.has-error {
  border-color: var(--danger-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--danger-color) 15%, transparent);
}

.error-text {
  display: block;
  margin-top: 6px;
  font-size: 0.8rem;
  color: var(--danger-color);
}

/* 种子输入行喵 */
.seed-input-row {
  display: flex;
  gap: 10px;
}

.seed-input {
  flex: 1;
}

.random-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  flex-shrink: 0;
}

/* 半径滑块喵 */
.radius-slider-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.radius-slider {
  flex: 1;
  -webkit-appearance: none;
  appearance: none;
  height: 6px;
  border-radius: 3px;
  background: color-mix(in srgb, var(--border-color) 60%, transparent);
  outline: none;
  cursor: pointer;
}

.radius-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--glow-color);
  cursor: pointer;
  box-shadow: 0 0 12px var(--glow-color);
  transition: transform 0.15s ease;
}

.radius-slider::-webkit-slider-thumb:hover {
  transform: scale(1.15);
}

.radius-number {
  width: 70px;
  text-align: center;
  padding: 10px;
}

/* 两列布局喵 */
.form-row-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

/* 选项卡片喵 */
.option-cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.option-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: color-mix(in srgb, var(--sa-bg1) 30%, transparent);
  cursor: pointer;
  transition: all 0.2s ease;
}

.option-card:hover {
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
  background: color-mix(in srgb, var(--sa-bg1) 50%, transparent);
  transform: translateX(4px);
}

.option-card.active {
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  box-shadow: 0 0 16px color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.card-icon {
  font-size: 1.4rem;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
}

.option-card.active .card-icon {
  background: color-mix(in srgb, var(--glow-color) 25%, transparent);
}

.card-content {
  flex: 1;
}

.card-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-color);
  margin-bottom: 2px;
}

.card-desc {
  font-size: 0.8rem;
  color: var(--sa-muted);
}

/* 底部按钮喵 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 24px;
  border-top: 1px solid color-mix(in srgb, var(--border-color) 50%, transparent);
  background: color-mix(in srgb, var(--sa-bg1) 20%, transparent);
  border-radius: 0 0 20px 20px;
}

.sa-btn {
  padding: 12px 24px;
  border-radius: 10px;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid var(--border-color);
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 8px;
}

.sa-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.cancel-btn {
  background: transparent;
}

.cancel-btn:hover {
  background: color-mix(in srgb, var(--sa-bg1) 40%, transparent);
  border-color: var(--glow-color);
}

.create-btn {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 30%, transparent),
    color-mix(in srgb, var(--glow-color) 15%, transparent)
  );
  border-color: color-mix(in srgb, var(--glow-color) 70%, transparent);
  color: var(--text-color-hover);
}

.create-btn:hover {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 45%, transparent),
    color-mix(in srgb, var(--glow-color) 25%, transparent)
  );
  box-shadow: 0 0 24px color-mix(in srgb, var(--glow-color) 40%, transparent);
  border-color: var(--glow-color);
}

/* 滚动条喵 */
.create-world-dialog::-webkit-scrollbar {
  width: 8px;
}

.create-world-dialog::-webkit-scrollbar-track {
  background: transparent;
}

.create-world-dialog::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--border-color) 60%, transparent);
  border-radius: 4px;
}

.create-world-dialog::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--glow-color) 60%, transparent);
}

/* 响应式设计喵 */
@media (max-width: 640px) {
  .create-world-overlay {
    padding: 12px;
  }

  .dialog-header {
    padding: 20px 16px 16px;
  }

  .header-icon {
    width: 48px;
    height: 48px;
  }

  .dialog-title {
    font-size: 1.3rem;
  }

  .dialog-body {
    padding: 16px;
  }

  .form-row-two {
    grid-template-columns: 1fr;
  }

  .radius-slider-row {
    flex-direction: column;
    align-items: stretch;
  }

  .radius-number {
    width: 100%;
  }

  .dialog-footer {
    padding: 16px;
    flex-direction: column-reverse;
  }

  .sa-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
