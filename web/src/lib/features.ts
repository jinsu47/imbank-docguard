// 화면 기능 스위치. 서버 API 는 그대로 두고 화면에서만 켜고 끕니다.

/** 시연 정확도 화면(F12). 사용자 요청으로 사이트에서 숨겼습니다. 다시 보이게 하려면 true 로 바꿉니다. */
export const SHOW_ACCURACY = false

/** 회원가입 화면. 공개 배포 빌드는 VITE_SIGNUP_ENABLED=false 로 끕니다(deploy/deploy.sh). 서버도 APP_SIGNUP_ENABLED 로 막습니다. */
export const SIGNUP_ENABLED = import.meta.env.VITE_SIGNUP_ENABLED !== 'false'
