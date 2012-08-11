package i3.swing;

import java.util.ArrayList;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

/**
 * A table of common lookandfeels.
 * @author fc29140
 */
public final class LookAndFeels {

    private LookAndFeels() {
        //nop
    }
    private final transient static String[] names = {
        "Substance Autumn",
        "Substance BusinessBlackSteel",
        "Substance BusinessBlueSteel",
        "Substance Business",
        "Substance ChallengerDeep",
        "Substance CremeCoffee",
        "Substance Creme",
        "Substance DustCoffee",
        "Substance Dust",
        "Substance EmeraldDusk",
        "Substance Gemini",
        "Substance GraphiteAqua",
        "Substance GraphiteGlass",
        "Substance Graphite",
        "Substance Magellan",
        "Substance MistAqua",
        "Substance MistSilver",
        "Substance Moderate",
        "Substance NebulaBrickWall",
        "Substance Nebula",
        "Substance OfficeBlue2007",
        "Substance OfficeSilver2007",
        "Substance Raven",
        "Substance Sahara",
        "Substance Twilight",
        "JGoodies Plastic",
        "JGoodies PlasticXP",
        "JGoodies Plastic3D",
        "JGoodies Windows",
        "Synthetica base",
        "Synthetica BlackMoon",
        "Synthetica BlackStar",
        "Synthetica BlueIce",
        "Synthetica BlueMoon",
        "Synthetica BlueSteel",
        "Synthetica GreenDream",
        "Synthetica MauveMetallic",
        "Synthetica OrangeMetallic",
        "Synthetica SkyMetallic",
        "Synthetica SilverMoon",
        "Synthetica WhiteVision",
        "A03",
        "Liquid",
        "Napkin",
        "Pagosoft",
        "Squareness"
    };
    private final transient static String[] classes = {
        "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceChallengerDeepLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceEmeraldDuskLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceOfficeBlue2007LookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceOfficeSilver2007LookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel",
        "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel",
        "com.jgoodies.looks.plastic.PlasticLookAndFeel",
        "com.jgoodies.looks.plastic.PlasticXPLookAndFeel",
        "com.jgoodies.looks.plastic.Plastic3DLookAndFeel",
        "com.jgoodies.looks.windows.WindowsLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaStandardLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaBlackMoonLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaBlackStarLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaBlueIceLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaBlueMoonLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaBlueSteelLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaGreenDreamLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaMauveMetallicLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaOrangeMetallicLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaSkyMetallicLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaSilverMoonLookAndFeel",
        "de.javasoft.plaf.synthetica.SyntheticaWhiteVisionLookAndFeel",
        "a03.swing.plaf.A03LookAndFeel",
        "com.birosoft.liquid.LiquidLookAndFeel",
        "net.sourceforge.napkinlaf.NapkinLookAndFeel",
        "com.pagosoft.plaf.PgsLookAndFeel",
        "net.beeger.squareness.SquarenessLookAndFeel"};
    private static transient boolean doneOnce;

    /**
     * The current LookAndFeel Info.
     * @return
     */
    public static LookAndFeelInfo getCurrentLookAndFeelInfo() {
        LookAndFeel installed = UIManager.getLookAndFeel();
        if (installed == null) {
            return null;
        }
        return new HumanReadableLookAndFeelInfo(installed.getName(), installed.getClass().getCanonicalName());
    }

    public static LookAndFeelInfo[] commonInstalledLookAndFeels() {
        if (!doneOnce) {
            doneOnce = true;

            List<LookAndFeelInfo> tmp = new ArrayList<>();

            //the default ones must be converted to have the same toString();
            for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
                tmp.add(new HumanReadableLookAndFeelInfo(i.getName(), i.getClassName()));
            }

            //additional ones found by matching the table.
            for (int i = 0; i < classes.length; i++) {
                try {
                    Class c = Class.forName(classes[i], false, ClassLoader.getSystemClassLoader());
                    if (c != null) {
                        tmp.add(new HumanReadableLookAndFeelInfo(names[i], classes[i]));
                    }
                } catch (Exception e) {
                    //skip
                }
            }
            UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[tmp.size()]));
        }
        return UIManager.getInstalledLookAndFeels();
    }

    private static class HumanReadableLookAndFeelInfo extends LookAndFeelInfo {

        public HumanReadableLookAndFeelInfo(String name, String className) {
            super(name, className);
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LookAndFeelInfo)) {
                return false;
            }
            LookAndFeelInfo other = (LookAndFeelInfo) obj;
            return getClassName().equals(other.getClassName());
        }

        @Override
        public int hashCode() {
            return getClassName().hashCode();
        }
    }
}
