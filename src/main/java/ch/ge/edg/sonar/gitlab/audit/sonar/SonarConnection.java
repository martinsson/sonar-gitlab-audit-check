package ch.ge.edg.sonar.gitlab.audit.sonar;

import org.jspecify.annotations.Nullable;

/**
 * Où et comment joindre l'instance.
 *
 * <p>L'URL et le jeton peuvent manquer : en rejeu, l'instance n'est jamais appelée. Une
 * organisation vide vaut absence — elle n'est envoyée qu'à SonarQube Cloud.
 */
public record SonarConnection(
    @Nullable String url,
    @Nullable String token,
    @Nullable String organization,
    int timeoutSeconds,
    boolean insecure) {}
