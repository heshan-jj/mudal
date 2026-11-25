#!/bin/bash

# Local release script that runs verification, bumps version/changelogs,
# commits, tags, and pushes so .github/workflows/release.yml builds & publishes.
# Usage: ./scripts/release.sh [patch|minor|major] [--yes] [--play] [--no-claude] [--dry-run]

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

CHANGELOG_FILE=""
CHANGELOG_DIR="fastlane/metadata/android/en-US/changelogs"
CHANGES_MADE=false

cleanup_on_exit() {
    if [ "$CHANGES_MADE" = true ]; then
        echo ""
        echo -e "${YELLOW}⚠️  Script interrupted. Reverting changes...${NC}"
        git checkout -- app/build.gradle.kts 2>/dev/null || true
        if [ -n "$CHANGELOG_FILE" ] && [ -f "$CHANGELOG_FILE" ]; then
            rm -f "$CHANGELOG_FILE"
        fi
        if [ -f "$CHANGELOG_DIR/default.txt" ]; then
            git checkout -- "$CHANGELOG_DIR/default.txt" 2>/dev/null || rm -f "$CHANGELOG_DIR/default.txt"
        fi
        echo -e "${YELLOW}Changes reverted.${NC}"
    fi
}

trap cleanup_on_exit EXIT INT TERM

VERSION_BUMP="patch"
DRY_RUN=""
AUTO_YES=""
DO_PLAY=""
NO_CLAUDE=""
for arg in "$@"; do
    case "$arg" in
        patch|minor|major) VERSION_BUMP="$arg" ;;
        -y|--yes)          AUTO_YES="true" ;;
        --play)            DO_PLAY="true" ;;
        --no-claude)       NO_CLAUDE="true" ;;
        --dry-run)         DRY_RUN="true"; echo -e "${YELLOW}🔍 DRY RUN MODE${NC}" ;;
        *) echo -e "${RED}Unknown argument: $arg${NC}"
           echo "Usage: $0 [patch|minor|major] [--yes] [--play] [--no-claude] [--dry-run]"
           exit 1 ;;
    esac
done

echo -e "${GREEN}🚀 Starting release (${VERSION_BUMP} bump)${NC}"

# 0. Pre-flight verification gate before modifying version/tags
if [ "$DRY_RUN" != "true" ]; then
    echo -e "${YELLOW}🧪 Running verification tests...${NC}"
    ./gradlew :parser-core:jvmTest :app:compileDebugKotlin :app:testDebugUnitTest
    echo -e "${GREEN}✅ Verification tests passed${NC}"
fi

# 1. Get current version
CURRENT_VERSION=$(grep "versionName = " app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
CURRENT_CODE=$(grep "versionCode = " app/build.gradle.kts | head -1 | sed 's/[^0-9]*//g')
echo "Current version: $CURRENT_VERSION (code: $CURRENT_CODE)"

# 2. Calculate next version
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

if [ "$VERSION_BUMP" = "major" ]; then
    MAJOR=$((MAJOR + 1))
    MINOR=0
    PATCH=0
elif [ "$VERSION_BUMP" = "minor" ]; then
    MINOR=$((MINOR + 1))
    PATCH=0
elif [ "$VERSION_BUMP" = "patch" ]; then
    PATCH=$((PATCH + 1))
fi

NEXT_VERSION="$MAJOR.$MINOR.$PATCH"
NEXT_CODE=$((CURRENT_CODE + 1))
echo "Next version: $NEXT_VERSION (code: $NEXT_CODE)"

# 3. Check if tag exists
TAG_NAME="v$NEXT_VERSION"
if git rev-parse "$TAG_NAME" >/dev/null 2>&1; then
    echo -e "${RED}❌ Tag $TAG_NAME already exists locally${NC}"
    exit 1
fi

# 4. Generate changelog
LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")

NOTES_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/release-notes"
NOTES_JSON=""
USE_CLAUDE=false

if [ -z "$NO_CLAUDE" ] && [ -n "$LAST_TAG" ] && command -v node &> /dev/null && command -v jq &> /dev/null; then
    PROCEED_AI=true
    if [ -z "$AUTO_YES" ]; then
        echo ""
        read -p "Generate release notes with Claude? (y/n) " -n 1 -r
        echo
        [[ $REPLY =~ ^[Yy]$ ]] || PROCEED_AI=false
    fi

    if [ "$PROCEED_AI" = true ]; then
        echo -e "${YELLOW}🤖 Generating release notes with the Claude Agent SDK...${NC}"
        if [ ! -d "$NOTES_DIR/node_modules" ]; then
            (cd "$NOTES_DIR" && npm ci --silent) || echo -e "${YELLOW}⚠️  npm ci failed${NC}"
        fi

        NOTES_RUN=(node "$NOTES_DIR/index.mjs")
        if command -v timeout &> /dev/null; then
            NOTES_RUN=(timeout "${RELEASE_NOTES_TIMEOUT:-120}" "${NOTES_RUN[@]}")
        elif command -v gtimeout &> /dev/null; then
            NOTES_RUN=(gtimeout "${RELEASE_NOTES_TIMEOUT:-120}" "${NOTES_RUN[@]}")
        fi

        NOTES_ERR=$(mktemp)
        COMMITS=$(git log "$LAST_TAG"..HEAD --pretty=format:"- %s")
        NOTES_EXIT=0
        NOTES_JSON=$(printf '%s' "$COMMITS" \
            | RELEASE_VERSION="$NEXT_VERSION" \
              RELEASE_NOTES_MODEL="${RELEASE_NOTES_MODEL:-claude-sonnet-4-6}" \
              "${NOTES_RUN[@]}" 2>"$NOTES_ERR") || NOTES_EXIT=$?

        if [ "$NOTES_EXIT" -eq 0 ] && [ -n "$NOTES_JSON" ] && printf '%s' "$NOTES_JSON" | jq -e . >/dev/null 2>&1; then
            USE_CLAUDE=true
        else
            echo -e "${YELLOW}⚠️  AI note generation failed (exit $NOTES_EXIT) — falling back to commit list.${NC}"
            NOTES_JSON=""
        fi
        rm -f "$NOTES_ERR"
    fi
fi

if [ "$DRY_RUN" = "true" ]; then
    echo -e "${YELLOW}🔍 DRY RUN SUMMARY${NC}"
    echo "=================="
    echo "Current version: $CURRENT_VERSION"
    echo "Next version: $NEXT_VERSION"
    echo "Version code: $NEXT_CODE"
    if [ -n "$LAST_TAG" ]; then
        echo ""
        echo "Changes since $LAST_TAG:"
        git log "$LAST_TAG"..HEAD --pretty=format:"- %s (%h)"
        echo ""
    fi
    exit 0
fi

# 5. Update version (versionName and versionCode)
if [[ "$OSTYPE" == "darwin"* ]]; then
    sed -i '' "s/versionName = \".*\"/versionName = \"$NEXT_VERSION\"/" app/build.gradle.kts
    sed -i '' '/val /!s/versionCode = .*/versionCode = '"$NEXT_CODE"'/' app/build.gradle.kts
else
    sed -i "s/versionName = \".*\"/versionName = \"$NEXT_VERSION\"/" app/build.gradle.kts
    sed -i '/val /!s/versionCode = .*/versionCode = '"$NEXT_CODE"'/' app/build.gradle.kts
fi
CHANGES_MADE=true
echo -e "${GREEN}✅ Version updated: $NEXT_VERSION (code: $NEXT_CODE)${NC}"

# 5a. Update fastlane changelog (bundled into app assets by copyChangelog)
CHANGELOG_FILE="$CHANGELOG_DIR/${NEXT_CODE}.txt"

if [ "$USE_CLAUDE" = true ] && [ -n "$NOTES_JSON" ]; then
    printf '%s' "$NOTES_JSON" \
        | jq -r '.highlights[]? | "• \(.)"' \
        | awk 'BEGIN{n=0} { add=length($0)+1; if (n+add>500) exit; print; n+=add }' \
        > "$CHANGELOG_FILE"
    if [ ! -s "$CHANGELOG_FILE" ]; then
        USE_CLAUDE=false
    fi
fi

if [ "$USE_CLAUDE" = false ]; then
    if [ -n "$LAST_TAG" ]; then
        echo "Version $NEXT_VERSION" > "$CHANGELOG_FILE"
        echo "" >> "$CHANGELOG_FILE"
        FEATURES=$(git log "$LAST_TAG"..HEAD --pretty=format:"%s" | grep "^feat" 2>/dev/null | sed -E 's/^feat(\([^)]+\))?: */• /' | head -5 || true)
        FIXES=$(git log "$LAST_TAG"..HEAD --pretty=format:"%s" | grep "^fix" 2>/dev/null | sed -E 's/^fix(\([^)]+\))?: */• /' | head -5 || true)

        if [ -n "$FEATURES" ]; then
            echo "New Features:" >> "$CHANGELOG_FILE"
            echo "$FEATURES" >> "$CHANGELOG_FILE"
            echo "" >> "$CHANGELOG_FILE"
        fi
        if [ -n "$FIXES" ]; then
            echo "Bug Fixes:" >> "$CHANGELOG_FILE"
            echo "$FIXES" >> "$CHANGELOG_FILE"
        fi
        if [ -z "$FEATURES" ] && [ -z "$FIXES" ]; then
            git log "$LAST_TAG"..HEAD --pretty=format:"• %s" | head -5 >> "$CHANGELOG_FILE"
        fi
    else
        echo "Initial release" > "$CHANGELOG_FILE"
    fi
fi

cp "$CHANGELOG_FILE" "$CHANGELOG_DIR/default.txt"
echo -e "${GREEN}✅ Fastlane changelog created: $CHANGELOG_FILE${NC}"

# 6. Commit and tag
git add app/build.gradle.kts "$CHANGELOG_FILE" "$CHANGELOG_DIR/default.txt"
git commit -m "chore(release): bump version to $NEXT_VERSION"
git tag -a "v$NEXT_VERSION" -m "Release v$NEXT_VERSION"
CHANGES_MADE=false
echo -e "${GREEN}✅ Commit and tag created${NC}"

# 7. Push to GitHub (GitHub Actions release.yml builds, signs, and publishes on v* tag)
if [ -n "$AUTO_YES" ]; then
    REPLY=y
else
    echo ""
    read -p "Push main and tag $TAG_NAME to origin (triggers GitHub Release workflow)? (y/n) " -n 1 -r
    echo
fi
if [[ $REPLY =~ ^[Yy]$ ]]; then
    git push origin main
    git push origin "$TAG_NAME"
    echo -e "${GREEN}✅ Pushed to GitHub — Actions will build and publish Release $TAG_NAME${NC}"
fi

# 8. Optional Play Store Bundle
if [ -n "$DO_PLAY" ]; then
    echo -e "${YELLOW}🔨 Building App Bundle for Play Store...${NC}"
    ./gradlew bundleRelease

    AAB_PATH="app/build/outputs/bundle/release"
    if [ -f "$AAB_PATH/app-release.aab" ]; then
        mv "$AAB_PATH/app-release.aab" "$AAB_PATH/Mudal-v${NEXT_VERSION}.aab"
        echo -e "${GREEN}✅ App Bundle created: $AAB_PATH/Mudal-v${NEXT_VERSION}.aab${NC}"
        if "$PWD/scripts/upload-play.sh" release; then
            echo -e "${GREEN}✅ Uploaded to Play Console as a DRAFT.${NC}"
        else
            echo -e "${RED}❌ Play upload failed.${NC} Retry manually: ./scripts/upload-play.sh release"
        fi
    else
        echo -e "${RED}❌ Expected $AAB_PATH/app-release.aab not found${NC}"
        exit 1
    fi
fi

echo ""
echo -e "${GREEN}✨ Release $NEXT_VERSION complete!${NC}"
