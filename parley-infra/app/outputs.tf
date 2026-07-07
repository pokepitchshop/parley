output "app_url" {
  description = "Public HTTPS base. Set Twilio voice webhook to <app_url>/voice/relay (ConversationRelay) or <app_url>/voice (turn-based)."
  value       = local.public_base_url
}
