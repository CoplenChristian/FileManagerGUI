package CoplenChristian.FileManagerGUI.util;

import java.text.DecimalFormat;

public final class HumanSize {
    private static final DecimalFormat DF1 = new DecimalFormat("#,##0.0");
    private HumanSize() {}

    public static String format(long bytes){
        if (bytes < 1024) return bytes + " B";
        double kib = bytes/1024.0;
        if (kib < 1024) return DF1.format(kib)+" KiB";
        double mib = kib/1024.0;
        if (mib < 1024) return DF1.format(mib)+" MiB";
        double gib = mib/1024.0;
        if (gib < 1024) return DF1.format(gib)+" GiB";
        double tib = gib/1024.0;
        return DF1.format(tib)+" TiB";
    }
}