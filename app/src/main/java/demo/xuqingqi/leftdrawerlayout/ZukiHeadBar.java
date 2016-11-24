package demo.xuqingqi.leftdrawerlayout;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

/**
 * Created on 2016/11/24.
 */

public class ZukiHeadBar extends RelativeLayout {

    public ZukiHeadBar(Context context) {
        this(context, null);
    }

    public ZukiHeadBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZukiHeadBar(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ZukiHeadBar(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs, defStyleAttr, defStyleRes);
    }

    private void init (Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ZukiHeadBar, defStyleAttr, defStyleRes);
        boolean showStatusBar = typedArray.getBoolean(R.styleable.ZukiHeadBar_showStatusBar, false);
        if (showStatusBar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            setPadding(0, getStatusBarHeight(), 0, 0);
        } else {
            setPadding(0, 0, 0, 0);
        }
    }

    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = this.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = this.getResources().getDimensionPixelOffset(resourceId);
        }
        return result;
    }

}
