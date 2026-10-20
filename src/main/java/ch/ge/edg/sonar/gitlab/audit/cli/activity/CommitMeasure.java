package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Ce que l'on sait des commits d'un projet sur la fenêtre.
 *
 * <p>« Pas de commit compté » et « zéro commit » sont deux constats différents, et c'est exactement
 * l'erreur que ce dépôt a déjà payée une fois côté Sonar : l'absence de mesure est donc une
 * variante, pas un zéro ni un null. Les accesseurs nullables ne servent qu'à publier.
 */
sealed interface CommitMeasure {

  /**
   * Le dernier commit de la branche par défaut : sa date et son SHA, chacun absent quand on ne l'a
   * pas lu.
   */
  record Head(@Nullable OffsetDateTime lastCommit, @Nullable String sha) {
    static Head unknown() {
      return new Head(null, null);
    }
  }

  /** Ce qu'une passe de pagination a compté. Les robots n'entrent que dans {@code bots}. */
  record Volume(int human, int bots, int authors, int merges, int activeDays, int reverts) {}

  /** Pas compté : écarté avant, ou laissé de côté par la coupe. */
  record NotCounted() implements CommitMeasure {}

  /**
   * Compté dans l'échantillon de contrôle du filtre de fraîcheur : un projet sous la coupe qui a
   * des commits dans la fenêtre est une fuite du filtre.
   */
  record Sampled(int commits) implements CommitMeasure {
    @Override
    public boolean leaked() {
      return commits > 0;
    }

    @Override
    public @Nullable Integer human() {
      return leaked() ? commits : null;
    }
  }

  /**
   * Le dernier commit, daté par GraphQL, est antérieur à la fenêtre : zéro par construction, sans
   * appel REST — mais ni merges, ni jours actifs, ni reverts n'ont été lus.
   */
  record NoneInWindow() implements CommitMeasure {
    @Override
    public Integer human() {
      return 0;
    }

    @Override
    public Integer bots() {
      return 0;
    }

    @Override
    public Integer authors() {
      return 0;
    }
  }

  /**
   * Compté par pagination REST.
   *
   * @param head la tête lue par GraphQL, complétée par la pagination
   * @param truncated la pagination a buté sur --max-commit-pages : le volume est un plancher
   */
  record Counted(Head head, Volume volume, boolean truncated) implements CommitMeasure {
    @Override
    public Integer human() {
      return volume.human();
    }

    @Override
    public Integer bots() {
      return volume.bots();
    }

    @Override
    public Integer authors() {
      return volume.authors();
    }

    @Override
    public Integer merges() {
      return volume.merges();
    }

    @Override
    public Integer activeDays() {
      return volume.activeDays();
    }

    @Override
    public Integer reverts() {
      return volume.reverts();
    }
  }

  /** Refus de permission ou erreur : « on ne sait pas », qui ne se lit pas « aucune activité ». */
  record Unmeasurable(String status) implements CommitMeasure {}

  /** Les commits humains, quand ils ont été comptés. */
  default @Nullable Integer human() {
    return null;
  }

  default @Nullable Integer bots() {
    return null;
  }

  default @Nullable Integer authors() {
    return null;
  }

  default @Nullable Integer merges() {
    return null;
  }

  default @Nullable Integer activeDays() {
    return null;
  }

  default @Nullable Integer reverts() {
    return null;
  }

  default boolean leaked() {
    return false;
  }

  /** Redéfini par le composant de {@link Counted}. */
  default boolean truncated() {
    return false;
  }

  /** Commits humains comptés, zéro quand ils ne l'ont pas été — pour trier, pas pour publier. */
  default int humanOrZero() {
    Integer n = human();
    return n == null ? 0 : n;
  }

  /** Des commits de robots et pas un seul humain : un constat, pas une ligne vide. */
  default boolean botOnly() {
    Integer h = human();
    Integer b = bots();
    return h != null && h == 0 && b != null && b > 0;
  }
}
