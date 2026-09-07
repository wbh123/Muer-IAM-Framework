<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(
  defineProps<{
    value: boolean | undefined | null;
    trueText?: string;
    falseText?: string;
  }>(),
  { trueText: '启用', falseText: '禁用' },
);

const type = computed(() => (props.value ? 'success' : 'info'));
const label = computed(() => (props.value ? props.trueText : props.falseText));
</script>

<template>
  <el-tag :type="type" size="small">{{ label }}</el-tag>
</template>
