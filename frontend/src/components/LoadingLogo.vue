<template>
  <div class="loading-logo" role="status" aria-live="polite">
    <div class="orb orb-a"></div>
    <div class="orb orb-b"></div>
    <div class="brand-core">
      <span>D</span>
      <i></i>
    </div>
    <strong>DokiAI 正在加载</strong>
    <small>正在进入智能创作工作流</small>
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
  background:
    radial-gradient(circle at 48% 42%, rgba(109, 93, 251, .18), transparent 22rem),
    radial-gradient(circle at 58% 58%, rgba(74, 144, 255, .12), transparent 24rem),
    rgba(255, 255, 255, .78);
  backdrop-filter: blur(22px);
}

.brand-core {
  position: relative;
  display: grid;
  place-items: center;
  width: 88px;
  height: 88px;
  border: 1px solid rgba(255, 255, 255, .85);
  border-radius: 26px;
  background: linear-gradient(145deg, #4a90ff 0%, #6d5dfb 56%, #df66b7 100%);
  box-shadow: 0 22px 70px rgba(109, 93, 251, .34), inset 0 1px 0 rgba(255, 255, 255, .46);
  animation: logo-breathe 1.55s ease-in-out infinite;
}

.brand-core span {
  color: #fff;
  font-size: 47px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -.08em;
  text-shadow: 0 6px 18px rgba(22, 28, 64, .25);
}

.brand-core i {
  position: absolute;
  inset: -14px;
  border-radius: 34px;
  border: 1px solid rgba(109, 93, 251, .28);
  animation: halo-expand 1.55s ease-out infinite;
}

.loading-logo strong {
  font-size: 18px;
  letter-spacing: .04em;
}

.loading-logo small {
  color: #6f7890;
}

.orb {
  position: absolute;
  border-radius: 999px;
  filter: blur(4px);
  opacity: .55;
  pointer-events: none;
}

.orb-a {
  width: 9px;
  height: 9px;
  background: #6d5dfb;
  animation: orbit-a 3.2s linear infinite;
}

.orb-b {
  width: 7px;
  height: 7px;
  background: #38d9c5;
  animation: orbit-b 3.7s linear infinite;
}

@keyframes logo-breathe {
  0%, 100% { transform: scale(.96); box-shadow: 0 18px 55px rgba(109, 93, 251, .28); }
  50% { transform: scale(1.04); box-shadow: 0 28px 82px rgba(109, 93, 251, .42); }
}

@keyframes halo-expand {
  from { transform: scale(.9); opacity: .72; }
  to { transform: scale(1.38); opacity: 0; }
}

@keyframes orbit-a {
  from { transform: translate(-72px, -22px) rotate(0deg) translateX(64px); }
  to { transform: translate(-72px, -22px) rotate(360deg) translateX(64px); }
}

@keyframes orbit-b {
  from { transform: translate(82px, 28px) rotate(360deg) translateX(58px); }
  to { transform: translate(82px, 28px) rotate(0deg) translateX(58px); }
}
</style>
