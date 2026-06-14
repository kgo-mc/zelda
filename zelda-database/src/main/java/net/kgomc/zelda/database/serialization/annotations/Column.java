package net.kgomc.zelda.database.serialization.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly maps a field to a database column name, overriding the default
 * snake_case → camelCase convention.
 *
 * <pre>{@code
 * public class ProfileDto {
 *     @Column("uuid")
 *     String id; // column is "uuid", field is "id"
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String value();
}