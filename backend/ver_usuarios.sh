#!/bin/bash
echo "=== Últimos usuarios y versiones conectadas ==="
ssh root@82.39.109.213 "pm2 logs backend-gerardo --lines 100 | grep -E 'REQ|StoreTD-Play|version|code=' | tail -30"
