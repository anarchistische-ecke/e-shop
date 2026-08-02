import InterfaceComponent from './interface.vue';

export default {
  id: 'storefront-entity-picker',
  name: 'Объект витрины',
  icon: 'manage_search',
  description: 'Поиск товаров, категорий, брендов, акций и промокодов без ручного ввода ID.',
  component: InterfaceComponent,
  types: ['string', 'uuid'],
  options: [
    {
      field: 'entity',
      name: 'Тип объекта',
      type: 'string',
      meta: {
        interface: 'select-dropdown',
        options: {
          choices: [
            { text: 'Товар', value: 'product' },
            { text: 'Категория', value: 'category' },
            { text: 'Бренд', value: 'brand' },
            { text: 'Акция', value: 'promotion' },
            { text: 'Промокод', value: 'promo_code' },
          ],
        },
        width: 'half',
      },
    },
    {
      field: 'endpoint',
      name: 'Endpoint моста',
      type: 'string',
      meta: { interface: 'input', width: 'full' },
    },
    {
      field: 'valueField',
      name: 'Поле значения',
      type: 'string',
      meta: { interface: 'input', width: 'half' },
    },
    {
      field: 'fallbackValueField',
      name: 'Резервное поле значения',
      type: 'string',
      meta: { interface: 'input', width: 'half' },
    },
    {
      field: 'labelField',
      name: 'Поле подписи',
      type: 'string',
      meta: { interface: 'input', width: 'half' },
    },
  ],
};
