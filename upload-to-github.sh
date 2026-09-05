#!/bin/bash
# DeepSeek Harness 移动端 - GitHub 上传脚本
# 使用方法: ./upload-to-github.sh <your-personal-access-token>
#
# 如何创建 Personal Access Token:
# 1. 登录 GitHub -> Settings -> Developer settings -> Personal access tokens -> Tokens (classic)
# 2. 点击 "Generate new token (classic)"
# 3. 勾选权限: repo (完整仓库访问), workflow
# 4. 生成并复制 token

set -e

GITHUB_TOKEN="$1"
REPO_OWNER="et2416444244"
REPO_NAME="deepseek-harness-mobile"
VERSION="v2.37"
APK_PATH="app/build/outputs/apk/release/app-release.apk"
APK_NAME="DeepSeek-Harness-Android-v2.37.apk"

if [ -z "$GITHUB_TOKEN" ]; then
    echo "错误: 请提供 GitHub Personal Access Token"
    echo "用法: $0 <your-token>"
    exit 1
fi

echo "=== 1. 创建 GitHub 仓库 ==="
curl -s -X POST "https://api.github.com/user/repos" \
    -H "Authorization: token $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    -d "{\"name\":\"$REPO_NAME\",\"description\":\"DeepSeek Harness 移动端 - 离线AI助手，支持本地模型推理，32/64位双架构\",\"private\":false,\"has_issues\":true,\"has_wiki\":true}" \
    | grep -E '"full_name"|"message"' || echo "仓库可能已存在"

echo ""
echo "=== 2. 推送代码到 GitHub ==="
git remote remove origin 2>/dev/null || true
git remote add origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${REPO_OWNER}/${REPO_NAME}.git"
git push -u origin main --force
echo "代码推送完成"

echo ""
echo "=== 3. 创建 GitHub Release ==="
RELEASE_NOTES="## v2.37 ETC+KU终极版

### 特性
- 🤖 本地模型推理 (0.5B, 完全离线使用)
- 📱 双架构支持: arm64-v8a (64位) + armeabi-v7a (32位)
- 🔒 ETC+KU R8代码混淆加固
- 🌙 暗黑模式 + 星空背景图开关
- 🔄 检测更新功能 (GitHub Release)
- 🔌 插件系统
- 🖥️ 局域网访问
- 📦 沙箱工作目录

### 修复
- ✅ 终极权限修复: 原生库放入jniLibs, 使用nativeLibraryDir (解决error=13)
- ✅ 基础Linux目录结构
- ✅ 模型解压进度实时显示
- ✅ 32位设备完整支持

### 系统要求
- Android 7.0+ (API 24+)
- arm64-v8a 或 armeabi-v7a 架构
- 至少 1GB 可用存储空间

### 开发者
- ETC (et2416444244@outlook.com)"

# 创建 release
RELEASE_RESPONSE=$(curl -s -X POST "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases" \
    -H "Authorization: token $GITHUB_TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    -d "{\"tag_name\":\"$VERSION\",\"name\":\"$VERSION ETC+KU终极版\",\"body\":\"$(echo "$RELEASE_NOTES" | sed 's/"/\\"/g' | sed ':a;N;$!ba;s/\n/\\n/g')\",\"draft\":false,\"prerelease\":false}")

RELEASE_ID=$(echo "$RELEASE_RESPONSE" | grep -o '"id": [0-9]*' | head -1 | grep -o '[0-9]*')
echo "Release ID: $RELEASE_ID"

if [ -z "$RELEASE_ID" ]; then
    echo "Release可能已存在，尝试获取已有release..."
    RELEASE_ID=$(curl -s "https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/tags/$VERSION" \
        -H "Authorization: token $GITHUB_TOKEN" | grep -o '"id": [0-9]*' | head -1 | grep -o '[0-9]*')
    echo "已有 Release ID: $RELEASE_ID"
fi

echo ""
echo "=== 4. 上传 APK 到 Release ==="
if [ -n "$RELEASE_ID" ] && [ -f "$APK_PATH" ]; then
    curl -s -X POST "https://uploads.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/${RELEASE_ID}/assets?name=${APK_NAME}" \
        -H "Authorization: token $GITHUB_TOKEN" \
        -H "Content-Type: application/vnd.android.package-archive" \
        --data-binary @"$APK_PATH" \
        | grep -E '"name"|"browser_download_url"' || echo "APK上传完成"
    echo "APK上传完成"
else
    echo "跳过APK上传 (RELEASE_ID或APK文件不存在)"
fi

echo ""
echo "=== 完成 ==="
echo "仓库地址: https://github.com/${REPO_OWNER}/${REPO_NAME}"
echo "Release地址: https://github.com/${REPO_OWNER}/${REPO_NAME}/releases/tag/$VERSION"
echo ""
echo "APK下载地址可在Release页面查看"
