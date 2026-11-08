# 네이버클라우드 배포 안내

구성은 서버 한 대입니다. Caddy가 HTTPS를 맡고, Spring Boot jar 하나가 화면과 API를 함께 제공하며, PostgreSQL 16은 같은 서버에 설치합니다. 주소는 `https://<공인IP의 점을 대시로 바꾼 값>.sslip.io`입니다.

## 1. 콘솔에서 서버 만들기

| 순서 | 메뉴 | 설정 |
| --- | --- | --- |
| 1 | Server > Server 생성 | Ubuntu 24.04, vCPU 2·메모리 8GB, 스토리지 50GB 이상. VPC와 Public Subnet은 기존 것을 써도 됩니다. |
| 2 | 서버 생성 중 인증키 | 기존 인증키를 쓰거나 새로 만듭니다. 관리자 비밀번호를 확인할 때 필요합니다. |
| 3 | 서버 생성 중 ACG | 새 ACG `docguard-acg`를 만듭니다. 다른 서버의 ACG를 함께 쓰지 않습니다. |
| 4 | Server > ACG > 규칙 설정 | Inbound: TCP 22는 **내 공인 IP/32**, TCP 80과 443은 `0.0.0.0/0` |
| 5 | Server > Public IP | 공인 IP를 신청해 서버에 연결합니다. |
| 6 | 서버 관리 및 설정 변경 > 관리자 비밀번호 확인 | 인증키 파일을 올려 root 비밀번호를 확인합니다. 첫 접속에만 씁니다. |

Subnet에 전용 Network ACL을 걸어 두었다면 Inbound에 TCP 80·443(`0.0.0.0/0`)을, Outbound에 응답용 TCP `1024-65535`(`0.0.0.0/0`)를 전체 차단 규칙보다 작은 우선순위 번호로 추가해야 합니다. Outbound가 빠지면 규칙에 등록된 IP에서만 접속되고 외부에서는 시간 초과가 납니다. 기본 Network ACL이면 고칠 것이 없습니다.

## 2. SSH 키 등록

PowerShell에서 실행합니다. 비밀번호를 물으면 6번의 root 비밀번호를 붙여 넣습니다.

```powershell
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@<공인IP> "mkdir -p ~/.ssh; chmod 700 ~/.ssh; tr -d '\r' >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys"
```

## 3. 설치와 배포

`IM-BANK` 디렉터리에서 실행합니다. 이 PC의 SSH 키는 Windows ssh-agent에 있으므로 `ssh`·`scp` 명령은 PowerShell에서 실행하고, `bash deploy/*.sh`는 Git Bash에서 실행합니다(스크립트가 Windows의 ssh를 사용합니다).

```bash
scp deploy/server-setup.sh deploy/seed-accounts.sh root@<공인IP>:/root/
ssh root@<공인IP> "bash /root/server-setup.sh <공인IP>"
bash deploy/deploy.sh <공인IP>
ssh root@<공인IP> "bash /root/seed-accounts.sh"
bash deploy/push-upstage-keys.sh <공인IP>
```

계정 비밀번호는 서버에서 확인합니다.

```bash
ssh root@<공인IP> "cat /var/lib/docguard/accounts.txt"
```

## 4. 다시 배포하기

```bash
bash deploy/deploy.sh <공인IP>
```

## 5. 운영 확인

```bash
ssh root@<공인IP> "systemctl status docguard --no-pager; journalctl -u docguard -n 50 --no-pager"
ssh root@<공인IP> "journalctl -u caddy -n 50 --no-pager"
```

인증서를 받지 못했다면 대개 ACG나 Network ACL의 80·443 규칙 문제입니다. Let's Encrypt는 같은 주소로 1시간에 5번 실패하면 발급을 막으므로, 방화벽을 고치는 동안에는 `systemctl stop caddy`로 멈춰 둡니다. 집이나 학교처럼 공인 IP가 바뀌면 ACG의 22번 규칙을 새 IP로 고칩니다.
