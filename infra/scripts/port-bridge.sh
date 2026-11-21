#!/bin/bash
# 端口桥容器 ds-portbridge 的幂等创建脚本（部署流程的必需一步）
#
# 背景：
#   kind 集群的 NodePort 只在「节点容器 IP」上可访问（例如 172.17.1.2:30432）。
#   Windows / WSL 宿主要直连这些端口，有两个办法：
#     A. 建集群时把端口写进 kind 的 extraPortMappings（推荐，见 kind-ds.yaml）——重启集群才生效；
#     B. 对已存在的集群，用本脚本起一个桥接容器，把宿主端口转发到节点 IP 上的同名 NodePort。
#
# 关键坑（已踩过）：
#   桥接容器如果放在默认 bridge 网络，访问 172.17.1.0/24 会 connect 超时
#   （docker 的 DOCKER-ISOLATION 规则隔离了不同网桥）。必须 --network kind。
#
# 用法： bash port-bridge.sh [--remove]
set -euo pipefail

NAME=ds-portbridge
NODE_CONTAINER=${NODE_CONTAINER:-ds-kind-control-plane}
PORTS=${PORTS:-"30432 30679 30672 30972 30848 30849 30990 30083"}
IMAGE=${IMAGE:-alpine/socat}

if [ "${1:-}" = "--remove" ]; then
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  echo "removed $NAME"
  exit 0
fi

NODE_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$NODE_CONTAINER")
[ -n "$NODE_IP" ] || { echo "找不到节点容器 $NODE_CONTAINER，集群没起来？"; exit 1; }
echo "节点容器 $NODE_CONTAINER IP = $NODE_IP"

# 已经存在且节点 IP 没变、端口列表没变、且接在 kind 网络 -> 直接复用
if docker inspect "$NAME" >/dev/null 2>&1; then
  CUR_IP=$(docker inspect -f '{{index .Config.Labels "ds.node.ip"}}' "$NAME" 2>/dev/null || true)
  CUR_PORTS=$(docker inspect -f '{{index .Config.Labels "ds.ports"}}' "$NAME" 2>/dev/null || true)
  CUR_NET=$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}} {{end}}' "$NAME" 2>/dev/null || true)
  RUNNING=$(docker inspect -f '{{.State.Running}}' "$NAME" 2>/dev/null || echo false)
  if [ "$CUR_IP" = "$NODE_IP" ] && [ "$CUR_PORTS" = "$PORTS" ] && [ "$RUNNING" = "true" ] && echo "$CUR_NET" | grep -q kind; then
    echo "$NAME 已存在且指向 $NODE_IP，端口列表未变，复用"
    docker ps --filter name="$NAME" --format '{{.Names}} {{.Status}}'
    exit 0
  fi
  echo "配置已变化（ip=$CUR_IP ports=[$CUR_PORTS] net=$CUR_NET running=$RUNNING），重建"
  docker rm -f "$NAME" >/dev/null 2>&1 || true
fi

ARGS=""
CMD=""
for p in $PORTS; do
  ARGS="$ARGS -p $p:$p"
  CMD="$CMD socat TCP-LISTEN:$p,fork,reuseaddr TCP:$NODE_IP:$p &"
done
CMD="$CMD wait"

docker run -d --name "$NAME" --restart unless-stopped --network kind \
  --label "ds.node.ip=$NODE_IP" \
  --label "ds.ports=$PORTS" \
  $ARGS --entrypoint sh "$IMAGE" -c "$CMD" >/dev/null

sleep 2
docker ps --filter name="$NAME" --format '{{.Names}} {{.Status}} {{.Ports}}'
echo "桥接完成：宿主(127.0.0.1) -> $NODE_IP 的 NodePort：$PORTS"
