#!/bin/bash

# AI Chat Repository Monitor Script
# Monitors GitHub Actions builds and downloads latest APK

REPO="UniverseKing4/aichat"
CHECK_INTERVAL=30
DOWNLOAD_DIR="$HOME/aichat-builds"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p "$DOWNLOAD_DIR"

echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     AI Chat Repository Monitor        ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
echo -e "${GREEN}Repository:${NC} $REPO"
echo -e "${GREEN}Download Directory:${NC} $DOWNLOAD_DIR"
echo -e "${GREEN}Check Interval:${NC} ${CHECK_INTERVAL}s\n"

get_latest_run() {
    gh api repos/$REPO/actions/runs \
        --jq '.workflow_runs[0] | {id: .id, status: .status, conclusion: .conclusion, created_at: .created_at, head_sha: .head_sha}'
}

get_latest_release() {
    gh api repos/$REPO/releases/latest \
        --jq '{tag: .tag_name, name: .name, created_at: .created_at, apk_url: .assets[0].browser_download_url, apk_name: .assets[0].name}' 2>/dev/null
}

download_apk() {
    local url=$1
    local name=$2
    local output="$DOWNLOAD_DIR/$name"
    
    if [ -f "$output" ]; then
        echo -e "${YELLOW}APK already exists: $name${NC}"
        return
    fi
    
    echo -e "${BLUE}Downloading: $name${NC}"
    curl -L -o "$output" "$url" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Downloaded: $output${NC}"
        echo -e "${GREEN}Size: $(du -h "$output" | cut -f1)${NC}\n"
    else
        echo -e "${RED}✗ Download failed${NC}\n"
    fi
}

LAST_RUN_ID=""
LAST_RELEASE_TAG=""

while true; do
    clear
    echo -e "${BLUE}╔════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║     AI Chat Repository Monitor        ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════╝${NC}"
    echo -e "${GREEN}Repository:${NC} https://github.com/$REPO"
    echo -e "${GREEN}Time:${NC} $(date '+%Y-%m-%d %H:%M:%S')\n"
    
    # Check workflow runs
    echo -e "${YELLOW}━━━ Latest Workflow Run ━━━${NC}"
    RUN_DATA=$(get_latest_run)
    
    if [ -n "$RUN_DATA" ]; then
        RUN_ID=$(echo "$RUN_DATA" | jq -r '.id')
        STATUS=$(echo "$RUN_DATA" | jq -r '.status')
        CONCLUSION=$(echo "$RUN_DATA" | jq -r '.conclusion')
        CREATED=$(echo "$RUN_DATA" | jq -r '.created_at')
        SHA=$(echo "$RUN_DATA" | jq -r '.head_sha' | cut -c1-7)
        
        echo -e "${GREEN}Run ID:${NC} $RUN_ID"
        echo -e "${GREEN}Commit:${NC} $SHA"
        echo -e "${GREEN}Status:${NC} $STATUS"
        
        if [ "$STATUS" = "completed" ]; then
            if [ "$CONCLUSION" = "success" ]; then
                echo -e "${GREEN}Result: ✓ SUCCESS${NC}"
            else
                echo -e "${RED}Result: ✗ $CONCLUSION${NC}"
            fi
        else
            echo -e "${YELLOW}Result: ⟳ In Progress...${NC}"
        fi
        
        echo -e "${GREEN}Started:${NC} $CREATED"
        
        if [ "$RUN_ID" != "$LAST_RUN_ID" ]; then
            if [ -n "$LAST_RUN_ID" ]; then
                echo -e "\n${BLUE}🔔 New workflow run detected!${NC}"
            fi
            LAST_RUN_ID=$RUN_ID
        fi
    else
        echo -e "${YELLOW}No workflow runs found${NC}"
    fi
    
    # Check releases
    echo -e "\n${YELLOW}━━━ Latest Release ━━━${NC}"
    RELEASE_DATA=$(get_latest_release)
    
    if [ -n "$RELEASE_DATA" ] && [ "$RELEASE_DATA" != "null" ]; then
        TAG=$(echo "$RELEASE_DATA" | jq -r '.tag')
        NAME=$(echo "$RELEASE_DATA" | jq -r '.name')
        CREATED=$(echo "$RELEASE_DATA" | jq -r '.created_at')
        APK_URL=$(echo "$RELEASE_DATA" | jq -r '.apk_url')
        APK_NAME=$(echo "$RELEASE_DATA" | jq -r '.apk_name')
        
        echo -e "${GREEN}Tag:${NC} $TAG"
        echo -e "${GREEN}Name:${NC} $NAME"
        echo -e "${GREEN}Created:${NC} $CREATED"
        echo -e "${GREEN}APK:${NC} $APK_NAME"
        
        if [ "$TAG" != "$LAST_RELEASE_TAG" ]; then
            if [ -n "$LAST_RELEASE_TAG" ]; then
                echo -e "\n${BLUE}🔔 New release detected!${NC}"
                download_apk "$APK_URL" "$APK_NAME"
            fi
            LAST_RELEASE_TAG=$TAG
        fi
    else
        echo -e "${YELLOW}No releases found yet${NC}"
    fi
    
    # Downloaded APKs
    echo -e "\n${YELLOW}━━━ Downloaded APKs ━━━${NC}"
    if [ "$(ls -A $DOWNLOAD_DIR 2>/dev/null)" ]; then
        ls -lh "$DOWNLOAD_DIR"/*.apk 2>/dev/null | awk '{print $9, "(" $5 ")"}'
    else
        echo -e "${YELLOW}No APKs downloaded yet${NC}"
    fi
    
    echo -e "\n${BLUE}Next check in ${CHECK_INTERVAL}s... (Ctrl+C to stop)${NC}"
    sleep $CHECK_INTERVAL
done
