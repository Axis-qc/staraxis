<script setup lang="ts">
// Defines the properties that this component accepts.
const props = defineProps<{
  visible: boolean // Controls whether the dialog is shown or hidden.
  title: string // The title text to display in the dialog.
  message: string // The main message content of the dialog.
}>()

// Defines the custom events that this component can emit.
const emit = defineEmits<{
  (e: 'close'): void // Event emitted when the close button is clicked.
}>()
</script>

<template>
  <!-- The dialog will only be rendered if the 'visible' prop is true. -->
  <div v-if="visible" class="dialog-backdrop">
    <div class="dialog-panel">
      <!-- Dialog Title -->
      <div class="dialog-header">
        <h2 class="dialog-title">{{ title }}</h2>
      </div>

      <!-- Dialog Body -->
      <div class="dialog-body">
        <p>{{ message }}</p>
      </div>

      <!-- Dialog Footer with Close Button -->
      <div class="dialog-footer">
        <button class="close-button" @click="emit('close')">确定</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Import the same futuristic font for consistency. */
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700&display=swap');

/* --- Backdrop --- */
/* Covers the entire screen with a semi-transparent dark layer. */
.dialog-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000; /* Ensures the dialog is on top of other content. */
  backdrop-filter: blur(5px);
  -webkit-backdrop-filter: blur(5px);
}

/* --- Dialog Panel --- */
/* The main container for the dialog content, styled with the sci-fi theme. */
.dialog-panel {
  width: 90%;
  max-width: 450px;
  padding: 30px;
  background: rgba(10, 25, 47, 0.8);
  border: 1px solid rgba(0, 191, 255, 0.5);
  border-radius: 16px;
  box-shadow: 0 0 25px rgba(0, 191, 255, 0.3);
  font-family: 'Orbitron', sans-serif;
  color: #e0e0e0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* --- Header & Title --- */
.dialog-header {
  text-align: center;
}

.dialog-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: #00bfff; /* Deep sky blue for a neon effect. */
  text-shadow: 0 0 8px #00bfff;
  margin: 0;
}

/* --- Body --- */
.dialog-body {
  font-size: 1rem;
  line-height: 1.6;
  text-align: center;
  color: #d0d0d0;
}

/* --- Footer & Button --- */
.dialog-footer {
  display: flex;
  justify-content: center;
}

.close-button {
  background: transparent;
  border: 1px solid rgba(0, 191, 255, 0.7);
  color: #e0e0e0;
  padding: 10px 30px;
  font-family: 'Orbitron', sans-serif;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.3s ease;
  text-transform: uppercase;
  border-radius: 8px;
}

.close-button:hover {
  background: rgba(0, 191, 255, 0.2);
  color: #ffffff;
  border-color: #00bfff;
  box-shadow: 0 0 15px rgba(0, 191, 255, 0.5);
}
</style>
