#!/usr/bin/env bash
set -e

JAVA17=/opt/homebrew/Cellar/openjdk@17/17.0.18/bin/java
JAVA_HOME_17=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home

JAVA_HOME="$JAVA_HOME_17" mvn clean package -q -DskipTests
mkdir -p ~/bin
cp target/self-agent-1.0-SNAPSHOT.jar ~/bin/self-agent.jar

cat > ~/bin/self-agent << EOF
#!/usr/bin/env bash
exec "$JAVA17" -jar "\$HOME/bin/self-agent.jar" "\$@"
EOF
chmod +x ~/bin/self-agent

# 自动添加 ~/bin 到 PATH（已在 PATH 中则跳过）
if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
    SHELL_RC=""
    if [ -f ~/.zshrc ]; then
        SHELL_RC=~/.zshrc
    elif [ -f ~/.bashrc ]; then
        SHELL_RC=~/.bashrc
    fi
    if [ -n "$SHELL_RC" ]; then
        echo '' >> "$SHELL_RC"
        echo '# self-agent' >> "$SHELL_RC"
        echo 'export PATH="$HOME/bin:$PATH"' >> "$SHELL_RC"
        echo "Added ~/bin to PATH in $SHELL_RC"
    fi
    export PATH="$HOME/bin:$PATH"
fi

echo "Installed to ~/bin/self-agent"
echo "Run: self-agent"
echo "Or restart your terminal if PATH not updated yet"
