<template>
  <component
    :is="as"
    class="dokiai-brand"
    :class="[
      `dokiai-brand--${size}`,
      {
        'dokiai-brand--stacked': stacked,
        'dokiai-brand--mark-only': markOnly,
        'dokiai-brand--interactive': interactive
      }
    ]"
    :type="as === 'button' ? 'button' : undefined"
    aria-label="DokiAI Academic"
  >
    <span class="dokiai-brand__icon" aria-hidden="true">
      <span>D</span>
    </span>
    <span v-if="!markOnly" class="dokiai-brand__copy">
      <strong>{{ title }}</strong>
      <small v-if="subtitle">{{ subtitle }}</small>
    </span>
  </component>
</template>

<script setup>
defineProps({
  as: {
    type: String,
    default: 'span'
  },
  title: {
    type: String,
    default: 'DokiAI Academic'
  },
  subtitle: {
    type: String,
    default: ''
  },
  size: {
    type: String,
    default: 'md',
    validator: value => ['sm', 'md', 'lg', 'xl'].includes(value)
  },
  stacked: {
    type: Boolean,
    default: false
  },
  markOnly: {
    type: Boolean,
    default: false
  },
  interactive: {
    type: Boolean,
    default: true
  }
})
</script>

<style scoped>
.dokiai-brand {
  --brand-size: 42px;
  --brand-radius: 13px;
  --brand-title: 18px;
  --brand-subtitle: 10px;
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  border: 0;
  color: #101733;
  background: transparent;
  text-align: left;
  text-decoration: none;
}

.dokiai-brand--sm {
  --brand-size: 34px;
  --brand-radius: 10px;
  --brand-title: 14px;
  --brand-subtitle: 9px;
}

.dokiai-brand--lg {
  --brand-size: 46px;
  --brand-radius: 14px;
  --brand-title: 19px;
  --brand-subtitle: 10px;
}

.dokiai-brand--xl {
  --brand-size: 68px;
  --brand-radius: 20px;
  --brand-title: 24px;
  --brand-subtitle: 11px;
}

.dokiai-brand--stacked {
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
}

.dokiai-brand__icon {
  position: relative;
  display: grid;
  flex: 0 0 auto;
  width: var(--brand-size);
  height: var(--brand-size);
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: var(--brand-radius);
  color: #fff;
  background:
    radial-gradient(circle at 28% 20%, rgba(255, 255, 255, 0.7), transparent 23%),
    linear-gradient(145deg, #4a90ff 0%, #6d5dfb 52%, #8d4dff 100%);
  box-shadow:
    0 14px 30px rgba(109, 93, 251, 0.34),
    0 0 24px rgba(109, 93, 251, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.46);
  isolation: isolate;
  transition:
    transform 0.3s ease,
    box-shadow 0.3s ease,
    filter 0.3s ease;
  animation: brandBoot 0.78s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.dokiai-brand__icon::before,
.dokiai-brand__icon::after {
  position: absolute;
  pointer-events: none;
  content: "";
}

.dokiai-brand__icon::before {
  inset: -28%;
  z-index: -1;
  background: conic-gradient(from 210deg, transparent 0 32%, rgba(255, 255, 255, 0.66), transparent 48% 100%);
  transform: translateX(-36%) rotate(12deg);
  opacity: 0.56;
}

.dokiai-brand__icon::after {
  right: 7px;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 0 16px rgba(255, 255, 255, 0.82);
}

.dokiai-brand__icon span {
  font-size: calc(var(--brand-size) * 0.52);
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.04em;
  text-shadow: 0 1px 10px rgba(25, 18, 86, 0.16);
}

.dokiai-brand__copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.dokiai-brand__copy strong {
  color: #111936;
  font-size: var(--brand-title);
  font-weight: 900;
  line-height: 1.1;
  letter-spacing: -0.02em;
  white-space: nowrap;
}

.dokiai-brand__copy small {
  color: #8b95ab;
  font-size: var(--brand-subtitle);
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: 0.02em;
  white-space: nowrap;
}

.dokiai-brand--interactive:hover .dokiai-brand__icon {
  filter: brightness(1.05);
  box-shadow:
    0 18px 38px rgba(109, 93, 251, 0.42),
    0 0 32px rgba(109, 93, 251, 0.32),
    inset 0 1px 0 rgba(255, 255, 255, 0.56);
  transform: scale(1.03);
}

@keyframes brandBoot {
  0% {
    opacity: 0;
    transform: scale(0.95);
    box-shadow:
      0 0 0 rgba(109, 93, 251, 0),
      0 0 0 0 rgba(109, 93, 251, 0.26),
      inset 0 1px 0 rgba(255, 255, 255, 0.38);
  }

  68% {
    opacity: 1;
    transform: scale(1.02);
    box-shadow:
      0 18px 38px rgba(109, 93, 251, 0.38),
      0 0 0 18px rgba(109, 93, 251, 0),
      inset 0 1px 0 rgba(255, 255, 255, 0.52);
  }

  100% {
    opacity: 1;
    transform: scale(1);
  }
}
</style>
