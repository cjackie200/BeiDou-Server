<template>
  <div class="navbar">
    <div class="left-side">
      <a-space>
        <img
          class="logo"
          alt="logo"
          src="/src/assets/logo.png"
          width="64"
          height="64"
        />
        <a-typography-title
          :style="{ margin: 0, fontSize: '18px' }"
          :heading="5"
        >
          {{ $t('title') }}
        </a-typography-title>
        <icon-menu-fold
          v-if="!topMenu && appStore.device === 'mobile'"
          style="font-size: 22px; cursor: pointer"
          @click="toggleDrawerMenu"
        />
      </a-space>
    </div>
    <div class="center-side">
      <Menu v-if="topMenu" />
    </div>
    <ul class="right-side">
      <li>
        <a-tag color="gray">{{ $t('settings.version') + ` ${version}` }}</a-tag>
      </li>
      <li>
        <a-tooltip :content="$t('settings.language')">
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="setDropDownVisible"
          >
            <template #icon>
              <icon-language />
            </template>
          </a-button>
        </a-tooltip>
        <a-dropdown trigger="click" @select="changeLocale as any">
          <div ref="triggerBtn" class="trigger-btn"></div>
          <template #content>
            <a-doption
              v-for="item in locales"
              :key="item.value"
              :value="item.value"
            >
              <template #icon>
                <icon-check v-show="item.value === currentLocale" />
              </template>
              {{ item.label }}
            </a-doption>
          </template>
        </a-dropdown>
      </li>
      <li>
        <a-tooltip
          :content="
            theme === 'light'
              ? $t('settings.switch.toDark')
              : $t('settings.switch.toLight')
          "
        >
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="handleToggleTheme"
          >
            <template #icon>
              <icon-moon-fill v-if="theme === 'dark'" />
              <icon-sun-fill v-else />
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <li>
        <a-tooltip
          :content="
            isFullscreen
              ? $t('settings.screen.toExit')
              : $t('settings.screen.toFull')
          "
        >
          <a-button
            class="nav-btn"
            type="outline"
            :shape="'circle'"
            @click="toggleFullScreen"
          >
            <template #icon>
              <icon-fullscreen-exit v-if="isFullscreen" />
              <icon-fullscreen v-else />
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <li>
        <a-dropdown trigger="click">
          <a-avatar
            :size="32"
            :style="{ marginRight: '8px', cursor: 'pointer' }"
            :src="avatar"
          />
          <template #content>
            <a-doption>
              <a-space @click="openPasswordModal">
                <icon-user />
                <span>{{ $t('settings.userCenter') }}</span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="openPasswordModal">
                <icon-settings />
                <span>{{ $t('settings.userSettings') }}</span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="handleLogout">
                <icon-export />
                <span>{{ $t('settings.logout') }}</span>
              </a-space>
            </a-doption>
          </template>
        </a-dropdown>
      </li>
    </ul>
    <a-modal
      v-model:visible="passwordModalVisible"
      title="修改登录密码"
      :ok-loading="passwordSubmitting"
      @ok="handleChangePassword"
      @cancel="resetPasswordForm"
    >
      <a-form ref="passwordFormRef" :model="passwordForm" layout="vertical">
        <a-form-item
          field="oldPwd"
          label="当前密码"
          :rules="[{ required: true, message: '请输入当前密码' }]"
        >
          <a-input-password
            v-model="passwordForm.oldPwd"
            autocomplete="current-password"
          />
        </a-form-item>
        <a-form-item
          field="newPwd"
          label="新密码"
          :rules="[
            { required: true, message: '请输入新密码' },
            { minLength: 6, message: '密码不能少于6位字符' },
          ]"
        >
          <a-input-password
            v-model="passwordForm.newPwd"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          field="newPwdCheck"
          label="确认新密码"
          :rules="[
            { required: true, message: '请再次输入新密码' },
            { validator: validatePasswordCheck },
          ]"
        >
          <a-input-password
            v-model="passwordForm.newPwdCheck"
            autocomplete="new-password"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
  import { computed, inject, reactive, ref } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { useDark, useToggle, useFullscreen } from '@vueuse/core';
  import { useAppStore, useUserStore } from '@/store';
  import useUser from '@/hooks/user';
  import Menu from '@/components/menu/index.vue';
  import useLocale from '@/hooks/locale';
  import { LOCALE_OPTIONS } from '@/locale';
  import { getVersion } from '@/api/dashboard';
  import { updateAccountByUser } from '@/api/account';
  import useLoading from '@/hooks/loading';

  const { changeLocale, currentLocale } = useLocale();
  const locales = [...LOCALE_OPTIONS];
  const appStore = useAppStore();
  const userStore = useUserStore();
  const { logout } = useUser();
  const { isFullscreen, toggle: toggleFullScreen } = useFullscreen();
  const avatar = computed(() => {
    // fixme 不存在的type
    return userStore.avatar;
  });
  const theme = computed(() => {
    return appStore.theme;
  });
  const topMenu = computed(() => appStore.topMenu && appStore.menu);
  const isDark = useDark({
    selector: 'body',
    attribute: 'arco-theme',
    valueDark: 'dark',
    valueLight: 'light',
    storageKey: 'arco-theme',
    onChanged(dark: boolean) {
      // overridden default behavior
      appStore.toggleTheme(dark);
    },
  });
  const toggleTheme = useToggle(isDark);
  const handleToggleTheme = () => {
    toggleTheme();
  };
  const handleLogout = () => {
    logout();
  };

  const passwordModalVisible = ref(false);
  const passwordSubmitting = ref(false);
  const passwordFormRef = ref();
  const passwordForm = reactive({
    oldPwd: '',
    newPwd: '',
    newPwdCheck: '',
  });
  const resetPasswordForm = () => {
    passwordForm.oldPwd = '';
    passwordForm.newPwd = '';
    passwordForm.newPwdCheck = '';
    passwordFormRef.value?.clearValidate?.();
  };
  const openPasswordModal = () => {
    resetPasswordForm();
    passwordModalVisible.value = true;
  };
  const validatePasswordCheck = (
    value: string | undefined,
    cb: (error?: string) => void
  ) => {
    if (value !== passwordForm.newPwd) {
      cb('两次输入的密码不匹配');
      return;
    }
    cb();
  };
  const handleChangePassword = async () => {
    const errors = await passwordFormRef.value?.validate?.();
    if (errors) {
      return false;
    }

    passwordSubmitting.value = true;
    try {
      await updateAccountByUser({
        oldPwd: passwordForm.oldPwd,
        newPwd: passwordForm.newPwd,
        language: userStore.language ?? 3,
      });
      Message.success('密码修改成功，请使用新密码重新登录');
      passwordModalVisible.value = false;
      resetPasswordForm();
      await logout();
    } finally {
      passwordSubmitting.value = false;
    }
    return true;
  };
  const toggleDrawerMenu = inject('toggleDrawerMenu') as () => void;

  const triggerBtn = ref();
  const setDropDownVisible = () => {
    const event = new MouseEvent('click', {
      view: window,
      bubbles: true,
      cancelable: true,
    });
    triggerBtn.value.dispatchEvent(event);
  };

  const version = ref<string>('');
  const { setLoading } = useLoading(false);
  const loadVersion = async () => {
    setLoading(true);
    try {
      const { data } = await getVersion();
      version.value = data;
    } finally {
      setLoading(false);
    }
  };
  loadVersion();
</script>

<style scoped lang="less">
  .navbar {
    display: flex;
    justify-content: space-between;
    height: 100%;
    background-color: var(--color-bg-2);
    border-bottom: 1px solid var(--color-border);
  }

  .left-side {
    display: flex;
    align-items: center;
    padding-left: 20px;
  }

  .center-side {
    flex: 1;
  }

  .right-side {
    display: flex;
    padding-right: 20px;
    list-style: none;
    :deep(.locale-select) {
      border-radius: 20px;
    }
    li {
      display: flex;
      align-items: center;
      padding: 0 10px;
    }

    a {
      color: var(--color-text-1);
      text-decoration: none;
    }
    .nav-btn {
      border-color: rgb(var(--gray-2));
      color: rgb(var(--gray-8));
      font-size: 16px;
    }
    .trigger-btn,
    .ref-btn {
      position: absolute;
      bottom: 14px;
    }
    .trigger-btn {
      margin-left: 14px;
    }
  }
</style>

<style lang="less">
  .message-popover {
    .arco-popover-content {
      margin-top: 0;
    }
  }
  .logo {
    filter: none; /* 默认不应用滤镜 */
  }

  [arco-theme='dark'] .logo {
    filter: invert(100%); /* 暗色主题下应用反色滤镜 */
  }
</style>
