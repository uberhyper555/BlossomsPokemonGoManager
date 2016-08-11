package me.corriekay.pokegoutil.utils.pokemon;

import POGOProtos.Networking.Responses.NicknamePokemonResponseOuterClass.NicknamePokemonResponse;
import com.pokegoapi.api.pokemon.Pokemon;
import com.pokegoapi.api.pokemon.PokemonMeta;
import com.pokegoapi.api.pokemon.PokemonMoveMeta;
import com.pokegoapi.api.pokemon.PokemonMoveMetaRegistry;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.PokeNames;
import me.corriekay.pokegoutil.utils.Config;
import me.corriekay.pokegoutil.utils.Utilities;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PokeHandler {
    public static final int MAX_NICKNAME_LENGTH = 12;

    private ArrayList<Pokemon> mons;

    public PokeHandler(Pokemon pokemon) {
        this(new Pokemon[]{pokemon});
    }

    public PokeHandler(Pokemon[] pokemon) {
        this(Arrays.asList(pokemon));
    }

    public PokeHandler(List<Pokemon> pokemon) {
        mons = new ArrayList<>(pokemon);
    }

    // region Static helper methods to handle Pokémon

    public static String generatePokemonNickname(String pattern, Pokemon pokemon) {
        return generatePokemonNickname(pattern, pokemon, getRenamePattern());
    }

    private static String generatePokemonNickname(String pattern, Pokemon pokemon, Pattern regex) {
        String pokeNick = pattern;
        Matcher m = regex.matcher(pattern);
        while (m.find()) {
            String fullExpr = m.group(1);
            String exprName = m.group(2);

            try {
                // Get ReplacePattern Object and use its get method
                // to get the replacement string.
                // Replace in nickname.
                ReplacePattern rep = ReplacePattern.valueOf(exprName.toUpperCase());
                String repStr = rep.get(pokemon);
                pokeNick = pokeNick.replace(fullExpr, repStr);
            } catch (IllegalArgumentException iae) {
                // Do nothing, nothing to replace
            }
        }
        return StringUtils.substring(pokeNick, 0, MAX_NICKNAME_LENGTH);
    }

    /**
     * Rename a single Pokemon based on a pattern
     *
     * @param pattern The pattern to use for renaming
     * @param pokemon The Pokemon to rename
     * @return The result of type <c>NicknamePokemonResponse.Result</c>
     */
    public static NicknamePokemonResponse.Result renameWithPattern(String pattern, Pokemon pokemon) {
        return renWPattern(pattern, pokemon, getRenamePattern());
    }

    /**
     * Helper method to get the rename Regex-Pattern so we don't have to rebuild
     * it every time we process a pokemon. This should save resources.
     */
    private static Pattern getRenamePattern() {
        return Pattern.compile("(%([a-zA-Z0-9_]+)%)");
    }

    /**
     * Renames a pokemon according to a regex pattern.
     *
     * @param pattern The pattern to use for renaming
     * @param pokemon The Pokemon to rename
     * @param regex   The regex to replace
     * @return The result of type <c>NicknamePokemonResponse.Result</c>
     */
    private static NicknamePokemonResponse.Result renWPattern(String pattern, Pokemon pokemon, Pattern regex) {
        String pokeNick = generatePokemonNickname(pattern, pokemon, regex);

        // Actually renaming the Pokémon with the calculated nickname
        try {
            NicknamePokemonResponse.Result result = pokemon.renamePokemon(pokeNick);
            return result;
        } catch (LoginFailedException | RemoteServerException e) {
            System.out.println("Error while renaming " + getLocalPokeName(pokemon) + "(" + pokemon.getNickname() + ")! " + Utilities.getRealExceptionMessage(e));
            return NicknamePokemonResponse.Result.UNRECOGNIZED;
        }
    }

    /**
     * Returns the Name for the Pokémon with <c>id</c> in the current language.
     *
     * @param id The Pokémon ID
     * @return The translated Pokémon name
     */
    public static String getLocalPokeName(int id) {
        // TODO: change call to getConfigItem to config class once implemented
        String lang = Config.getConfig().getString("options.lang", "en");

        Locale locale;
        String[] langar = lang.split("_");
        if (langar.length == 1) {
            locale = new Locale(langar[0]);
        } else {
            locale = new Locale(langar[0], langar[1]);
        }

        String name = null;
        try {
            name = new String(PokeNames.getDisplayName(id, locale).getBytes("ISO-8859-1"), "UTF-8");
            name = StringUtils.capitalize(name.toLowerCase());
            name = name.replaceAll("_male", "♂").replaceAll("_female", "♀");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return name;
    }

    /**
     * Returns the Name of the Pokémon <c>pokemon</c> in the current language.
     *
     * @param pokemon The Pokémon
     * @return The translated Pokémon name
     */
    public static String getLocalPokeName(Pokemon pokemon) {
        return getLocalPokeName(pokemon.getPokemonId().getNumber());
    }

    // endregion

    /**
     * Rename a bunch of Pokemon based on a pattern
     *
     * @param pattern         The pattern to use for renaming
     * @param perPokeCallback Will be called for each Pokémon that has been (tried) to
     *                        rename.
     * @return A <c>LinkedHashMap</c> with each Pokémon as key and the result as
     * value.
     */
    public LinkedHashMap<Pokemon, NicknamePokemonResponse.Result> bulkRenameWithPattern(String pattern,
                                                                                        BiConsumer<NicknamePokemonResponse.Result, Pokemon> perPokeCallback) {

        LinkedHashMap<Pokemon, NicknamePokemonResponse.Result> results = new LinkedHashMap<>();

        Pattern regex = getRenamePattern();
        mons.forEach(p -> {
            NicknamePokemonResponse.Result result = renWPattern(pattern, p, regex);
            if (perPokeCallback != null) {
                perPokeCallback.accept(result, p);
            }
            results.put(p, result);
        });

        return results;
    }

    public void bulkRenameWithPattern(String pattern) {
        bulkRenameWithPattern(pattern, null);
    }

    /**
     * This enum represents the definition of placeholder strings for Pokémon
     * renaming. To add a new placeholder rule, add a new enum constant, pass a
     * friendly Name as constructor parameter and override the <c>get</c> method
     * to return what should be the replacement for the enum. The enum constant
     * serves as placeholder.
     * <p>
     * Example: String "%cp%_%name%" for a 200cp Magikarp will result in a
     * renaming of that Magikarp to "200_Magicarp".
     */
    public enum ReplacePattern {
        NICK("Nickname") {
            @Override
            public String get(Pokemon p) {
                return p.getNickname();
            }
        },
        NAME("Pokémon Name") {
            @Override
            public String get(Pokemon p) {
                return getLocalPokeName(p);
            }
        },
        NAME_4("Pokémon Name (First four letters)") {
            @Override
            public String get(Pokemon p) {
                String name = getLocalPokeName(p);
                return (name.length() <= 4) ? name : name.substring(0, 3)+".";
            }
        },
        NAME_6("Pokémon Name (First six letters)") {
            @Override
            public String get(Pokemon p) {
                String name = getLocalPokeName(p);
                return (name.length() <= 4) ? name : name.substring(0, 5)+".";
            }
        },
        NAME_8("Pokémon Name (First eight letters)") {
            @Override
            public String get(Pokemon p) {
                String name = getLocalPokeName(p);
                return (name.length() <= 8) ? name : name.substring(0, 7)+".";
            }
        },
        CP("Combat Points") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getCp());
            }
        },
        HP("Hit Points") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getMaxStamina());
            }
        },
        LEVEL("Pokémon Level") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getLevel());
            }
        },
        IV_RATING("IV Rating") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(Math.round(p.getIvRatio() * 100 * 100) / 100.0);
            }
        },
        IV_RATING_INT("IV Rating (Rounded to integer)") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(Math.round(p.getIvRatio() * 100));
            }
        },
        IV_HEX("IV Values in hexadecimal, like \"9FA\" (F = 15)") {
            @Override
            public String get(Pokemon p) {
                return (Integer.toHexString(p.getIndividualAttack()) + Integer.toHexString(p.getIndividualDefense()) + Integer.toHexString(p.getIndividualStamina())).toUpperCase();
            }
        },
        IV_ATT("IV Attack") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getIndividualAttack());
            }
        },
        IV_DEF("IV Defense") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getIndividualDefense());
            }
        },
        IV_STAM("IV Stamina") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getIndividualStamina());
            }
        },
        MAX_CP("Maximum possible CP (with Trainer Level 40)") {
            @Override
            public String get(Pokemon p) {
                PokemonMeta meta = p.getMeta();
                int attack = meta.getBaseAttack() + p.getIndividualAttack();
                int defense = meta.getBaseDefense() + p.getIndividualDefense();
                int stamina = meta.getBaseStamina() + p.getIndividualStamina();
                return String.valueOf(PokemonCpUtils.getMaxCp(attack, defense, stamina));
            }
        },
        MOVE_TYPE1("Type of Move 1") {
            @Override
            public String get(Pokemon p) {
                String type = PokemonMoveMetaRegistry.getMeta(p.getMove1()).getType().toString();
                return type.substring(0,1).toUpperCase()+type.substring(1).toLowerCase();
            }
        },
        MOVE_TYPE2("Type of Move 2") {
            @Override
            public String get(Pokemon p) {
                String type = PokemonMoveMetaRegistry.getMeta(p.getMove2()).getType().toString();
                return type.substring(0,1).toUpperCase()+type.substring(1).toLowerCase();
            }
        },
        MOVE_TYPE1_SHORT("Type of Move 1 abbreviated (Ghost = Gh)") {
            @Override
            public String get(Pokemon p) {
                String type = PokemonMoveMetaRegistry.getMeta(p.getMove1()).getType().toString();
                return abbreviateType(type);
            }
        },
        MOVE_TYPE2_SHORT("Type of Move 2 abbreviated (Ghost = Gh)") {
            @Override
            public String get(Pokemon p) {
                String type = PokemonMoveMetaRegistry.getMeta(p.getMove2()).getType().toString();
                return abbreviateType(type);
            }
        },
        DPS_1("Damage per second for Move 1") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(Math.round(PokemonUtils.dpsForMove1(p)));
            }
        },
        DPS_2("Damage per second for Move 2") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(Math.round(PokemonUtils.dpsForMove2(p)));
            }
        },
        TYPE_1("Pokémon Type 1 (First two letters)") {
            @Override
            public String get(Pokemon p) {
                return StringUtils.substring(StringUtils.capitalize(p.getMeta().getType1().toString().toLowerCase()), 0, 2);
            }
        },
        TYPE_2("Pokémon Type 2 (First two letters)") {
            @Override
            public String get(Pokemon p) {
                return StringUtils.substring(StringUtils.capitalize(p.getMeta().getType2().toString().toLowerCase().replaceAll("none", "")), 0, 2);
            }
        },
        ID("Pokédex Id") {
            @Override
            public String get(Pokemon p) {
                return String.valueOf(p.getPokemonId().getNumber());
            }
        };

        private static String abbreviateType(String type){
            if(type.equalsIgnoreCase("fire")||type.equalsIgnoreCase("ground")){
                return type.substring(0,1).toUpperCase() + type.substring(type.length()-1).toLowerCase();
            }else{
                return type.substring(0,1).toUpperCase() + type.substring(1,2).toLowerCase();
            }

        }

        private final String friendlyName;

        ReplacePattern(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        /**
         * Returns the friendly name for the replacement pattern. Can be used to
         * generate explanations for possible placeholders automatically.
         */
        public String toString() {
            return friendlyName;
        }

        /**
         * This method must be overwritten and should return what should be the
         * replacement.
         *
         * @param p The Pokémon that receives the new nick name.
         * @return The value that the placeholder should be replaced with
         */
        public abstract String get(Pokemon p);
    }
}
