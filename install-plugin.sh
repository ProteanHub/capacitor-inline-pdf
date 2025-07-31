#!/bin/bash

# Build and install the Capacitor Inline PDF plugin

echo "Building Capacitor Inline PDF plugin..."

# Navigate to plugin directory
cd "$(dirname "$0")"

# Install dependencies
echo "Installing plugin dependencies..."
npm install

# Build the plugin
echo "Building plugin..."
npm run build

# Go back to main project directory
cd ../..

# Install the plugin in the main project
echo "Installing plugin in main project..."
npm install ./plugins/capacitor-inline-pdf

# Sync with native platforms
echo "Syncing with native platforms..."
npx cap sync

echo "Plugin installation complete!"
echo ""
echo "Next steps:"
echo "1. For iOS: Run 'npx cap open ios' and build in Xcode"
echo "2. For Android: Run 'npx cap open android' and build in Android Studio"
echo "3. Test the native PDF viewer on actual devices"