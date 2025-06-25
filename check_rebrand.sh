#!/bin/bash

# Set expected new app/package info
NEW_PACKAGE="com.gaaius.wallet"
NEW_APP_NAME="GAAIUS WALLET"

# Color helpers
green="\033[0;32m"
red="\033[0;31m"
reset="\033[0m"

echo -e "🔍 Starting GAAIUS WALLET rebrand check..."

echo -e "\n1️⃣ Checking package name in files:"
grep -r "com.alphawallet.app" ./src ./AndroidManifest.xml ./build.gradle > /dev/null
if [ $? -eq 0 ]; then
  echo -e "${red}❌ Found old package name 'com.alphawallet.app'.${reset}"
  grep -r --color=always "com.alphawallet.app" ./src ./AndroidManifest.xml ./build.gradle
else
  echo -e "${green}✅ Package name updated correctly to ${NEW_PACKAGE}.${reset}"
fi

echo -e "\n2️⃣ Checking app name in strings.xml:"
if grep -q "$NEW_APP_NAME" ./res/values/strings.xml; then
  echo -e "${green}✅ App name is '$NEW_APP_NAME'.${reset}"
else
  echo -e "${red}❌ App name '$NEW_APP_NAME' not found in strings.xml.${reset}"
fi

echo -e "\n3️⃣ Searching for any leftover 'AlphaWallet' branding:"
FOUND=$(grep -r "AlphaWallet" . | wc -l)
if [ $FOUND -gt 0 ]; then
  echo -e "${red}❌ Found $FOUND leftover 'AlphaWallet' references.${reset}"
  grep -r --color=always "AlphaWallet" .
else
  echo -e "${green}✅ No 'AlphaWallet' references found.${reset}"
fi

echo -e "\n4️⃣ Checking if app icons were changed:"
ORIG_ICON="res/mipmap-xxhdpi/ic_launcher.png"
if [ -f "$ORIG_ICON" ]; then
  SIZE=$(stat -c%s "$ORIG_ICON")
  if [ $SIZE -lt 3000 ]; then
    echo -e "${red}❌ Icon at $ORIG_ICON might still be default (very small file size: $SIZE bytes).${reset}"
  else
    echo -e "${green}✅ Icon seems to have been replaced (file size: $SIZE bytes).${reset}"
  fi
else
  echo -e "${red}❌ Icon file $ORIG_ICON not found.${reset}"
fi

echo -e "\n✅ Rebrand check complete."
