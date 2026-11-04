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
    aria-label="Dokiai Academic"
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
    default: 'Dokiai'
  },
  subtitle: {
    type: String,
    default: 'Academic'
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
  --brand-size: 36px;
  --brand-title: 16px;
  --brand-subtitle: 11px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 0;
  color: #1c2430;
  background: transparent;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
}

.dokiai-brand--sm {
  --brand-size: 30px;
  --brand-title: 14px;
  --brand-subtitle: 10px;
}

.dokiai-brand--lg {
  --brand-size: 40px;
  --brand-title: 18px;
  --brand-subtitle: 12px;
}

.dokiai-brand--xl {
  --brand-size: 52px;
  --brand-title: 22px;
  --brand-subtitle: 13px;
}

.dokiai-brand--stacked {
  display: grid;
  justify-items: center;
  gap: 10px;
  text-align: center;
}

.dokiai-brand__icon {
  display: grid;
  flex: 0 0 auto;
  width: var(--brand-size);
  height: var(--brand-size);
  place-items: center;
  border-radius: 4px;
  color: #fff;
  background: #1e3a5f;
}

.dokiai-brand__icon span {
  font-family: "Songti SC", "STSong", "SimSun", serif;
  font-size: calc(var(--brand-size) * 0.52);
  font-weight: 600;
  line-height: 1;
}

.dokiai-brand__copy {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.dokiai-brand__copy strong {
  color: #1c2430;
  font-family: "Songti SC", "STSong", "Noto Serif SC", "SimSun", serif;
  font-size: var(--brand-title);
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: 0;
  white-space: nowrap;
}

.dokiai-brand__copy small {
  color: #5c6b7a;
  font-size: var(--brand-subtitle);
  font-weight: 500;
  line-height: 1.2;
  letter-spacing: 0.04em;
  white-space: nowrap;
}

.dokiai-brand--interactive:hover .dokiai-brand__icon {
  background: #17314f;
}
</style>
