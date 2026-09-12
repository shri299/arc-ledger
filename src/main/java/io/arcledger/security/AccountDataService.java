package io.arcledger.security;

import io.arcledger.domain.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.sql.ResultSetMetaData;
import java.util.*;

@Service
public class AccountDataService {
    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUser;
    private final PasswordEncoder passwords;

    public AccountDataService(JdbcTemplate jdbc, CurrentUserService currentUser, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.passwords = passwords;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export() {
        AppUser user = currentUser.require();
        UUID userId = user.getId();
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("id", userId);
        account.put("email", user.getEmail());
        account.put("displayName", user.getDisplayName());
        account.put("emailVerifiedAt", user.getEmailVerifiedAt());
        account.put("createdAt", user.getCreatedAt());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("format", "arcledger-account-export-v1");
        result.put("generatedAt", Instant.now());
        result.put("account", account);
        result.put("stories", rows("SELECT id, title, description, created_at, updated_at FROM stories WHERE owner_id = ? ORDER BY created_at", userId));
        result.put("chapters", ownedRows("SELECT chapter.id, chapter.story_id, chapter.chapter_number, chapter.title, chapter.created_at, chapter.updated_at FROM chapters chapter", userId));
        result.put("scenes", ownedRows("SELECT scene.id, scene.story_id, scene.chapter_id, scene.sequence_number, scene.raw_text, scene.processing_status, scene.created_at, scene.updated_at FROM scenes scene", userId));
        result.put("entities", ownedRows("SELECT entity.id, entity.story_id, entity.name, entity.normalized_name, entity.type, entity.latest_version, entity.created_at, entity.updated_at FROM narrative_entities entity", userId));
        result.put("stateVersions", ownedRows("SELECT state_version.id, state_version.story_id, state_version.entity_id, state_version.originating_scene_id, state_version.version_number, state_version.changed_facts_json, state_version.resulting_state_json, state_version.created_at FROM entity_state_versions state_version", userId));
        result.put("facts", rows("SELECT fact.id, fact.entity_id, fact.state_version_id, fact.source_scene_id, fact.fact_key, fact.fact_value, fact.knowledge_kind, fact.active, fact.superseded_by_fact_id, fact.created_at FROM entity_facts fact JOIN narrative_entities entity ON entity.id = fact.entity_id JOIN stories story ON story.id = entity.story_id WHERE story.owner_id = ? ORDER BY fact.created_at", userId));
        result.put("syntheticQuestions", ownedRows("SELECT question.id, question.story_id, question.entity_id, question.state_version_id, question.scene_id, question.question, question.answer, question.current_state, question.created_at FROM synthetic_questions question", userId));
        result.put("consistencyResults", ownedRows("SELECT consistency.id, consistency.story_id, consistency.scene_id, consistency.entity_id, consistency.status, consistency.severity, consistency.description, consistency.supporting_evidence, consistency.source_scene_ids, consistency.created_at FROM consistency_results consistency", userId));
        result.put("collaborators", rows("SELECT membership.story_id, membership.user_id, member.email, member.display_name, membership.role, membership.created_at FROM story_memberships membership JOIN stories story ON story.id = membership.story_id JOIN app_users member ON member.id = membership.user_id WHERE story.owner_id = ? ORDER BY membership.created_at", userId));
        return result;
    }

    @Transactional
    public UUID delete(String currentPassword) {
        AppUser user = currentUser.require();
        if (!passwords.matches(currentPassword, user.getPasswordHash())) throw new PasswordConfirmationException();
        UUID userId = user.getId();
        String email = user.getEmail();

        deleteOwned("consistency_results", userId);
        deleteOwned("synthetic_questions", userId);
        jdbc.update("DELETE FROM entity_facts WHERE entity_id IN (SELECT entity.id FROM narrative_entities entity JOIN stories story ON story.id = entity.story_id WHERE story.owner_id = ?)", userId);
        deleteOwned("entity_state_versions", userId);
        deleteOwned("narrative_entities", userId);
        jdbc.update("DELETE FROM scene_processing_jobs WHERE scene_id IN (SELECT scene.id FROM scenes scene JOIN stories story ON story.id = scene.story_id WHERE story.owner_id = ?)", userId);
        deleteOwned("scenes", userId);
        deleteOwned("chapters", userId);
        jdbc.update("DELETE FROM story_invitations WHERE story_id IN (SELECT id FROM stories WHERE owner_id = ?) OR invited_by = ? OR invited_email = ?", userId, userId, email);
        jdbc.update("DELETE FROM story_memberships WHERE story_id IN (SELECT id FROM stories WHERE owner_id = ?) OR user_id = ?", userId, userId);
        jdbc.update("DELETE FROM stories WHERE owner_id = ?", userId);
        jdbc.update("DELETE FROM account_session_metadata WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM spring_session WHERE principal_name = ?", email);
        jdbc.update("DELETE FROM account_tokens WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM account_recovery_codes WHERE user_id = ?", userId);
        jdbc.update("UPDATE security_audit_events SET actor_id = NULL WHERE actor_id = ?", userId);
        jdbc.update("DELETE FROM app_users WHERE id = ?", userId);
        return userId;
    }

    private List<Map<String, Object>> ownedRows(String select, UUID userId) {
        return rows(select + " JOIN stories story ON story.id = " + alias(select) + ".story_id WHERE story.owner_id = ? ORDER BY " + alias(select) + ".created_at", userId);
    }

    private List<Map<String, Object>> rows(String sql, UUID userId) {
        return jdbc.query(sql, (row, index) -> {
            ResultSetMetaData metadata = row.getMetaData();
            Map<String, Object> values = new LinkedHashMap<>();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                values.put(camelCase(metadata.getColumnLabel(column)), row.getObject(column));
            }
            return values;
        }, userId);
    }

    private void deleteOwned(String table, UUID userId) {
        jdbc.update("DELETE FROM " + table + " WHERE story_id IN (SELECT id FROM stories WHERE owner_id = ?)", userId);
    }

    private static String alias(String select) {
        int from = select.toUpperCase(Locale.ROOT).lastIndexOf(" FROM ");
        return select.substring(from + 6).strip().split("\\s+")[1];
    }

    private static String camelCase(String value) {
        StringBuilder result = new StringBuilder();
        boolean uppercaseNext = false;
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if (character == '_') uppercaseNext = true;
            else if (uppercaseNext) { result.append(Character.toUpperCase(character)); uppercaseNext = false; }
            else result.append(character);
        }
        return result.toString();
    }
}
