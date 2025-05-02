#!/bin/bash

echo "Building AnonymizeAttachmentFilenames plugin..."
cd ./AliucordPlugins
./gradlew :AnonymizeAttachmentFilenames:make

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"
echo "Removing old plugin version..."
adb shell rm /storage/emulated/0/Aliucord/plugins/AnonymizeAttachmentFilenames*.zip

echo "Installing new plugin version..."



adb push ./AnonymizeAttachmentFilenames/build/AnonymizeAttachmentFilenames.zip /storage/emulated/0/Aliucord/plugins/

echo "Stopping Discord..."
adb shell am force-stop com.discord

echo "✅ Plugin deployed successfully!"
echo "Please start Discord manually and upload a file to test the plugin."
echo ""
echo "Showing logs from AnonymizeAttachmentFilenames and Aliucord..."
echo "Look for 'Anonymizing filename' or 'Anonymizing attachment filename' messages in the logs."
echo ""

adb logcat -c
adb -s 192.168.0.107:41285 logcat -s Discord:V | grep "\[AnonymizeAttachmentFilenames\]"