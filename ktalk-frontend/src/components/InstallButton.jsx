import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Bi } from '../EnglishHint'

const isStandalone = () =>
  window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true
const isIos = () => /iphone|ipad|ipod/i.test(window.navigator.userAgent)
// 카카오톡/네이버/인스타그램 등 앱 안에서 열린 화면(인앱 브라우저)에서는 홈 화면 추가가 막혀 있다.
const isInAppBrowser = () => /KAKAOTALK|NAVER\(inapp|Instagram|FBAN|FBAV|Line\/|DaumApps/i.test(window.navigator.userAgent)

// iOS 공유 버튼(네모 위에 화살표) 모양. 글로 설명하는 것보다 실제 아이콘을 보여주는 편이 찾기 쉽다.
function ShareIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 15V3" />
      <path d="M8 7l4-4 4 4" />
      <path d="M6 11H5a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-8a1 1 0 0 0-1-1h-1" />
    </svg>
  )
}

const STEPS = [
  {
    ko: '주소창 안(또는 화면 아래)에 있는 공유 버튼을 누르세요. 네모 위에 화살표(↑)가 있는 모양이에요.',
    en: 'Tap the Share button in the address bar (or at the bottom). It looks like a box with an arrow (↑).',
    icon: <ShareIcon />,
  },
  {
    ko: '메뉴를 아래로 밀어서 "홈 화면에 추가"를 누르세요.',
    en: 'Scroll down the menu and tap "Add to Home Screen".',
    icon: '➕',
  },
  {
    ko: '오른쪽 위의 "추가"를 누르면 끝! 홈 화면에 K-Talk 아이콘이 생겨요.',
    en: 'Tap "Add" at the top right. Done! The K-Talk icon appears on your home screen.',
    icon: '✅',
  },
]

function IosInstallGuide({ onClose }) {
  const inApp = isInAppBrowser()
  const [copied, setCopied] = useState(false)

  // 공유 메뉴에 "홈 화면에 추가"가 없는 화면(앱 안 브라우저 등)에서는 Safari로 옮겨야 하므로 주소를 복사해 준다.
  const copyLink = async () => {
    const url = window.location.origin + '/'
    try {
      await navigator.clipboard.writeText(url)
    } catch {
      const input = document.createElement('input')
      input.value = url
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    setCopied(true)
  }

  return createPortal(
    <div className="ios-install-overlay" onClick={onClose}>
      <div className="ios-install-card" role="dialog" aria-modal="true" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="ios-install-close" onClick={onClose} aria-label="닫기">×</button>
        <h2><Bi en="Install on iPhone">아이폰에서 앱 설치하기</Bi></h2>

        {inApp && (
          <p className="ios-install-warn">
            <Bi en="You are inside another app's browser. Open this page in Safari first (menu → Open in Safari), then follow the steps.">
              지금은 카카오톡/네이버 같은 앱 안에서 열린 화면이에요. 메뉴에서 &quot;Safari로 열기&quot;를 눌러 Safari에서 먼저 열어 주세요.
            </Bi>
          </p>
        )}

        <ol className="ios-install-steps">
          {STEPS.map((step, index) => (
            <li key={index}>
              <span className="ios-install-icon">{step.icon}</span>
              <span><b>{index + 1}.</b> <Bi en={step.en}>{step.ko}</Bi></span>
            </li>
          ))}
        </ol>

        <div className="ios-install-fallback">
          <b><Bi en="Can't find &quot;Add to Home Screen&quot;?">공유 메뉴에 &quot;홈 화면에 추가&quot;가 안 보이나요?</Bi></b>
          <p>
            <Bi en="You are probably in a browser inside another app. Copy the link, open the Safari app, paste it into the address bar, then repeat the steps above.">
              다른 앱 안에서 열린 화면일 수 있어요. 아래 버튼으로 주소를 복사한 뒤, 아이폰의 <b>Safari 앱</b>을 열어 주소창에 붙여넣고 위 단계를 다시 해 주세요.
            </Bi>
          </p>
          <button type="button" className="ios-install-copy" onClick={copyLink}>
            <Bi en={copied ? 'Copied! Paste it in Safari.' : 'Copy link'}>{copied ? '복사됨! Safari에 붙여넣으세요' : '주소 복사'}</Bi>
          </button>
          <small className="ios-install-url">{window.location.origin}/</small>
        </div>

        <button type="button" className="ios-install-ok" onClick={onClose}><Bi en="Got it">알겠어요</Bi></button>
      </div>
    </div>,
    document.body,
  )
}

/**
 * "앱 설치"(홈 화면에 바로가기 추가) 버튼. 크롬/엣지/안드로이드는 브라우저가 알려주는
 * 설치 프롬프트를 띄우고, 프롬프트가 없는 iOS는 단계별 안내 창(공유 → 홈 화면에 추가)을 보여준다.
 * 이미 설치된 앱(standalone)으로 열렸으면 아무것도 그리지 않는다.
 */
function InstallButton() {
  const [installEvent, setInstallEvent] = useState(null)
  const [showIosHelp, setShowIosHelp] = useState(false)
  const [installed, setInstalled] = useState(() => isStandalone())

  useEffect(() => {
    const onPrompt = (event) => {
      event.preventDefault()
      setInstallEvent(event)
    }
    const onInstalled = () => { setInstalled(true); setInstallEvent(null) }
    window.addEventListener('beforeinstallprompt', onPrompt)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onPrompt)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  if (installed || (!installEvent && !isIos())) return null

  const handleClick = async () => {
    if (installEvent) {
      installEvent.prompt()
      await installEvent.userChoice
      setInstallEvent(null)
    } else {
      setShowIosHelp(true)
    }
  }

  return (
    <>
      <button type="button" className="login-button" onClick={handleClick}>
        <Bi en="Install app">앱 설치</Bi>
      </button>
      {showIosHelp && <IosInstallGuide onClose={() => setShowIosHelp(false)} />}
    </>
  )
}

export default InstallButton
