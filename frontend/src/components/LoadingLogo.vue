<template>
  <div class="loading-logo" role="status" aria-live="polite">
    <div class="brand-core">
      <span>D</span>
      <i></i>
    </div>
    <strong>Dokiai 正在打开</strong>
    <small>请稍候</small>
  </div>
</template>

<style scoped>
.loading-logo {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 14px;
  overflow: hidden;
  color: #121833;
  background: rgba(244, 246, 248, 0.94);
}

.brand-core {
  position: relative;
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  border-radius: 4px;
  background: #1e3a5f;
}

.brand-core span {
  color: #fff;
  font-size: 47px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -.08em;
  text-shadow: 0 6px 18px rgba(22, 28, 64, .25);
}

.brand-core i,
.orb {
  display: none;
}

.loading-logo strong {
  font-size: 18px;
}

.loading-logo small {
  color: #5c6b7a;
}
</style>
