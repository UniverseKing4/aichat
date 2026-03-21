#!/bin/bash

REPO="UniverseKing4/aichat"
DOWNLOAD_DIR="/storage/emulated/0/Download"

echo "🔍 Monitoring build..."
sleep 10

while true; do
    STATUS=$(gh run list --repo "$REPO" --limit 1 --json status,conclusion --jq '.[0] | "\(.status) \(.conclusion)"')
    
    if [[ "$STATUS" == "completed success" ]]; then
        LATEST_TAG=$(gh release list --repo "$REPO" --limit 1 --json tagName --jq '.[0].tagName')
        echo "✅ Build successful! Version: $LATEST_TAG"
        echo "📥 Downloading APK..."
        
        gh release download "$LATEST_TAG" --repo "$REPO" --pattern "*.apk" --dir "$DOWNLOAD_DIR" --clobber
        
        echo "✓ APK saved to $DOWNLOAD_DIR"
        break
        
    elif [[ "$STATUS" == "completed failure" ]]; then
        echo "❌ Build failed!"
        echo ""
        echo "📋 Error logs:"
        
        RUN_ID=$(gh run list --repo "$REPO" --limit 1 --json databaseId --jq '.[0].databaseId')
        gh run view "$RUN_ID" --repo "$REPO" --log-failed
        
        echo ""
        echo "Fix the errors and push again."
        break
        
    elif [[ "$STATUS" == "in_progress "* ]] || [[ "$STATUS" == "queued "* ]]; then
        echo "⏳ Building..."
        sleep 5
    else
        echo "Status: $STATUS"
        sleep 5
    fi
done
