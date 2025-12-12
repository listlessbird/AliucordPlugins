#!/bin/bash

if [ -d "/usr/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/opt/android-studio/jbr" ]; then
    export JAVA_HOME="/opt/android-studio/jbr"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

mapfile -t PLUGINS < <(find plugins -maxdepth 1 -mindepth 1 -type d -exec basename {} \; | sort)

validate_plugin() {
    local plugin=$1
    for p in "${PLUGINS[@]}"; do
        if [[ "$p" == "$plugin" ]]; then
            return 0
        fi
    done
    return 1
}

select_device() {
    echo "Checking for connected devices..."
    local devices
    mapfile -t devices < <(adb devices | grep -v "List of devices" | grep -E "device$|unauthorized$|offline$" | awk '{print $1}')

    if [ ${#devices[@]} -eq 0 ]; then
        echo "Error: No ADB devices found!"
        echo "Please connect your device via USB or wireless ADB."
        echo "Run 'adb devices' to check connected devices."
        exit 1
    elif [ ${#devices[@]} -eq 1 ]; then
        DEVICE="${devices[0]}"
        echo "Using device: $DEVICE"
    else
        echo "Multiple devices found:"
        for i in "${!devices[@]}"; do
            echo "$((i+1)). ${devices[$i]}"
        done
        echo ""
        read -p "Select a device (1-${#devices[@]}): " choice

        if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "${#devices[@]}" ]; then
            DEVICE="${devices[$((choice-1))]}"
            echo "Using device: $DEVICE"
        else
            echo "Invalid selection!"
            exit 1
        fi
    fi
}

select_plugin() {
    echo "Available plugins:"
    for i in "${!PLUGINS[@]}"; do
        echo "$((i+1)). ${PLUGINS[$i]}"
    done
    echo ""
    read -p "Select a plugin (1-${#PLUGINS[@]}): " choice

    if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "${#PLUGINS[@]}" ]; then
        PLUGIN="${PLUGINS[$((choice-1))]}"
    else
        echo "Invalid selection!"
        exit 1
    fi
}

if [ -n "$1" ]; then
    PLUGIN="$1"
    # Validate that the plugin exists
    if ! validate_plugin "$PLUGIN"; then
        echo "Error: Plugin '$PLUGIN' not found!"
        echo "Available plugins: ${PLUGINS[*]}"
        exit 1
    fi
else
    select_plugin
fi

select_device

echo "Building $PLUGIN plugin..."
./gradlew ":plugins:$PLUGIN:make"

if [ $? -ne 0 ]; then
    echo "--------- Build failed! ---------"
    exit 1
fi

echo "--------- Build successful! ---------"
echo "Removing old plugin version..."
adb -s "$DEVICE" shell rm /storage/emulated/0/Aliucord/plugins/${PLUGIN}*.zip

echo "Installing new plugin version..."



adb -s "$DEVICE" push ./plugins/$PLUGIN/build/outputs/${PLUGIN}.zip //storage/emulated/0/Aliucord/plugins/

echo "Stopping Discord..."
adb -s "$DEVICE" shell am force-stop com.aliucord

echo "✅ Plugin deployed successfully!"
echo "Please start Discord manually to test the plugin."
echo ""
echo "Showing logs from $PLUGIN and Aliucord..."
echo "Look for '[$PLUGIN]' messages in the logs."
echo ""

adb -s "$DEVICE" logcat -c
adb -s "$DEVICE" logcat -s Discord:V | grep "\\[$PLUGIN\\]"
