import { defineModule } from '@directus/extensions-sdk';
import ModuleComponent from './module.vue';

export default defineModule({
  id: 'storefront-ops',
  name: 'Управление витриной',
  icon: 'shop_2',
  routes: [
    {
      path: '',
      component: ModuleComponent,
    },
  ],
});
