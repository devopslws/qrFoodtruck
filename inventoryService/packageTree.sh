#!/bin/bash
# ============================================
# 도메인 구조 생성 스크립트
# 위치: 각 서비스 모듈 최상위 (build.gradle 옆)
#
# [설정] 아래 두 변수를 서비스에 맞게 수정하세요
BASE_PACKAGE="com.example.inventoryService"   # 패키지명
SRC_BASE="src/main/java"                  # 소스 루트 (변경 불필요)
# ============================================
#
# 사용법: ./mkstructure.sh <도메인명> [도메인명2] ...
# 예시:   ./mkstructure.sh order payment
#         ./mkstructure.sh review
# ============================================

set -e

if [ $# -lt 1 ]; then
  echo "사용법: $0 <도메인명> [도메인명2] ..."
  echo "예시:   $0 order payment"
  exit 1
fi

DOMAINS=("$@")
PACKAGE_PATH="${BASE_PACKAGE//./\/}"
BASE="$SRC_BASE/$PACKAGE_PATH"

echo "============================================"
echo " 도메인 구조 생성"
echo " 패키지: $BASE_PACKAGE"
echo " 도메인: ${DOMAINS[*]}"
echo "============================================"

for domain in "${DOMAINS[@]}"; do
  DOMAIN_BASE="$BASE/$domain"

  # 디렉토리 생성
  for subdir in entity repository service controller dto; do
    mkdir -p "$DOMAIN_BASE/$subdir"
  done

  # 클래스명 (첫글자 대문자)
  CLASS_NAME="$(tr '[:lower:]' '[:upper:]' <<< "${domain:0:1}")${domain:1}"

  # service
  cat > "$DOMAIN_BASE/service/${CLASS_NAME}Service.java" << JAVA
package ${BASE_PACKAGE}.${domain}.service;

public class ${CLASS_NAME}Service {
}
JAVA

  # controller
  cat > "$DOMAIN_BASE/controller/${CLASS_NAME}Controller.java" << JAVA
package ${BASE_PACKAGE}.${domain}.controller;

public class ${CLASS_NAME}Controller {
}
JAVA

  # dto
  cat > "$DOMAIN_BASE/dto/${CLASS_NAME}Request.java" << JAVA
package ${BASE_PACKAGE}.${domain}.dto;

public class ${CLASS_NAME}Request {
}
JAVA

  cat > "$DOMAIN_BASE/dto/${CLASS_NAME}Response.java" << JAVA
package ${BASE_PACKAGE}.${domain}.dto;

public class ${CLASS_NAME}Response {
}
JAVA

  echo "[CREATE] $domain/ (entity, repository, service, controller, dto)"
done

echo ""
echo "============================================"
echo " 완료!"
echo "============================================"