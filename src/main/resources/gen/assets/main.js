import { ref, computed, reactive, createApp } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.js';

createApp({
  setup() {
    const defaultBadge = {
      url: 'https://file.fishpi.cn/logo_raw.png',
      txt: '摸鱼者事竟成',
      backcolor: ['#ed8f26'],
      fontcolor: [],
      shadow: 0.8,
      way: 'top-left',
      deg: '0',
      fontway: 'bottom',
      fontDeg: '0',
      fontsize: 15,
      anime: 5,
      scale: 0.79,
      border: 3,
      size: 32,
      barradiusAuto: true,
      barlenAuto: true,
      fontname: '',
    };
    const badge = ref({ ...defaultBadge });

    const genAttr = computed(() => {
      const attrs = [];
      attrs.push(`url="${badge.value.url}"`);
      attrs.push(`backcolor="${badge.value.backcolor.join(',')}"`);
      if (badge.value.fontcolor.length > 0) {
        attrs.push(`fontcolor="${badge.value.fontcolor.join(',')}"`);
      }
      if (badge.value.backcolor.length > 1) {
        attrs.push(`way="${badge.value.way === 'custom' ? `${badge.value.deg}deg` : badge.value.way}"`);
      }
      if (badge.value.fontcolor.length > 1) {
        attrs.push(`fontway="${badge.value.fontway === 'custom' ? `${badge.value.fontDeg}deg` : badge.value.fontway}"`);
      }
      if (!badge.value.barlenAuto && badge.value.barlen && Number(badge.value.barlen) > 0) {
        attrs.push(`barlen="${badge.value.barlen}"`);
      }
      if (badge.value.fontname && badge.value.txt) {
        attrs.push(`font="${getFontDataUrlForText(badge.value.txt)}"`);
      }
      return attrs.join('&');
    })

    const genUrl = computed(() => {
      const params = new URLSearchParams();
      params.append('url', badge.value.url);
      if (badge.value.txt) params.append('txt', badge.value.txt);
      if (Number(badge.value.scale) != 1) {
        params.append('scale', badge.value.scale);
      }
      params.append('backcolor', badge.value.backcolor.join(',').replace(/#/g, ''));
      if (badge.value.fontcolor.length > 0) {
        params.append('fontcolor', badge.value.fontcolor.join(',').replace(/#/g, ''));
      }
      if (Number(badge.value.shadow) != 0.8) {
        params.append('shadow', badge.value.shadow);
      }
      if (badge.value.backcolor.length > 1) {
        params.append('way', badge.value.way === 'custom' ? `${badge.value.deg}deg` : badge.value.way);
      }
      if (badge.value.fontcolor.length > 1) {
        params.append('fontway', badge.value.fontway === 'custom' ? `${badge.value.fontDeg}deg` : badge.value.fontway);
      }
      if (badge.value.anime != 5) {
        params.append('anime', badge.value.anime);
      }
      if (Number(badge.value.fontsize) != 15) {
        params.append('fontsize', badge.value.fontsize);
      }
      if (Number(badge.value.border) != 3) {
        params.append('border', badge.value.border);
      }
      if (Number(badge.value.size) != 32) {
        params.append('size', badge.value.size);
      }
      if (!badge.value.barradiusAuto && badge.value.barradius && Number(badge.value.barradius) != Math.floor(badge.value.size / 2)) {
        params.append('barradius', badge.value.barradius);
      }
      if (!badge.value.barlenAuto && badge.value.barlen && Number(badge.value.barlen) > 0) {
        params.append('barlen', badge.value.barlen);
      }
      if (badge.value.fontname && badge.value.txt) {
        params.append('font', getFontDataUrlForText(badge.value.txt));
      }
      return `/gen?${params.toString()}`;
    });

    const dictIcon = reactive({
      top: 'arrow-up',
      bottom: 'arrow-down',
      'top-left': 'arrow-up-left',
      'top-right': 'arrow-up-right',
      'bottom-left': 'arrow-down-left',
      'bottom-right': 'arrow-down-right',
      left: 'arrow-left',
      right: 'arrow-right',
      custom: 'compass',
    });

    function setBarRadius(checked) {
      badge.value.barradiusAuto = checked;
      if (!checked) {
        badge.value.barradius = badge.value.size / 2;
      }
    }

    function reset() {
      badge.value = { ...defaultBadge };
    }

    async function loadedFont(ev) {
      const file = await loadFont(ev).catch(e => {
        console.error('加载字体失败', e);
      });
      if (file) {
        console.log('已加载字体文件：', file.name);
        badge.value.fontname = file.name;
      }
    }

    return {
      loadedFont,
      location,
      reset,
      dictIcon,
      badge,
      genUrl,
      setBarRadius,
    };
  }
}).mount('#app');
