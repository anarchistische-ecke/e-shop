import { defineModule } from '@directus/extensions-sdk';
import ModuleComponent from './module.vue';

export default defineModule({
  id: 'storefront-ops',
  name: 'Storefront Ops',
  icon: 'storefront',
  routes: [
    {
      path: '',
      component: ModuleComponent,
    },
  ],
});
