import { Client, type IMessage } from "@stomp/stompjs";

type EventEnvelope = {
  eventId?: string;
  type: string;
  seq?: number;
  orderId?: string;
  lineId?: string;
  status?: string;
  version?: number;
  changedBy?: string;
  ingredientName?: string;
  ingredientId?: string;
  affectedOpenOrderIds?: string[];
};

export function connectKds(storeId: string, onEvent: (event: EventEnvelope) => void,
  onConnection: (connected: boolean) => void): () => void {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const address = `/topic/kds/${storeId}`;
  const seen = new Set<string>(JSON.parse(localStorage.getItem("qros-kds-events") ?? "[]") as string[]);
  const accept = (message: IMessage) => {
    const event = JSON.parse(message.body) as EventEnvelope;
    if (event.eventId && seen.has(event.eventId)) return;
    if (event.eventId) {
      seen.add(event.eventId);
      localStorage.setItem("qros-kds-events", JSON.stringify([...seen].slice(-300)));
    }
    if (typeof event.seq === "number") localStorage.setItem(`qros-kds-seq:${storeId}`, String(event.seq));
    onEvent(event);
  };
  const client = new Client({
    brokerURL: `${protocol}//${location.host}/api/v1/staff/ws`,
    reconnectDelay: 1_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    onConnect: () => {
      onConnection(true);
      client.subscribe(address, accept);
      client.subscribe("/user/queue/resume", accept);
      client.publish({ destination: "/app/resume", headers: { "content-type": "application/json" }, body: JSON.stringify({ channels: [{
        address, lastSeq: Number(localStorage.getItem(`qros-kds-seq:${storeId}`) ?? 0),
      }] }) });
    },
    onWebSocketClose: () => onConnection(false),
    onStompError: () => onConnection(false),
  });
  client.activate();
  return () => { void client.deactivate(); };
}
