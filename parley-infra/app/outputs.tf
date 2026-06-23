output "app_url" {
  description = "Public HTTPS base. Set Twilio voice webhook to <app_url>/voice/relay (ConversationRelay) or <app_url>/voice (turn-based)."
  value       = "https://${azurerm_container_app.parley.ingress[0].fqdn}"
}
