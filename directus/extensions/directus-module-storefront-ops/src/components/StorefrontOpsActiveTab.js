import { h } from 'vue';

export default {
  name: 'StorefrontOpsActiveTab',
  props: {
    component: {
      type: [Object, Function],
      required: true,
    },
    viewProps: {
      type: Object,
      required: true,
    },
  },
  setup(props) {
    return () => h(props.component, props.viewProps);
  },
};
