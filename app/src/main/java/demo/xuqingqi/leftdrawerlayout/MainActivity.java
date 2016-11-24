package demo.xuqingqi.leftdrawerlayout;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.drawable.DrawerArrowDrawable;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import demo.xuqingqi.leftdrawerlayout.drawer.LeftDrawerLayout;

public class MainActivity extends AppCompatActivity {

    private NavigationView navigationView;
    private LeftDrawerLayout drawerLayout;
    private View headerView;
    private ImageView iv_main_story;
    private View head_bar;
    private View rl_toggle;
    private ImageView iv_toggle;
    private DrawerArrowDrawable arrowDrawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            }
        }

        setContentView(R.layout.activity_main);

        iv_main_story = findView(R.id.iv_main_story);
        navigationView = findView(R.id.navigation_view);
        drawerLayout = findView(R.id.drawer);
        head_bar = findView(R.id.head_bar);
        rl_toggle = findView(R.id.rl_toggle);
        iv_toggle = findView(R.id.iv_toggle);

        headerView = navigationView.getHeaderView(0);
        TextView nameText = (TextView) headerView.findViewById(R.id.header_username);
        nameText.setText("xuqingqi");
        RelativeLayout header = (RelativeLayout) headerView.findViewById(R.id.header);
        header.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.closeDrawer();
            }
        });

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(MenuItem item) {
                drawerLayout.closeDrawer();
                return true;
            }
        });

        arrowDrawable = new DrawerArrowDrawable(this);
        arrowDrawable.setColor(Color.WHITE);
        iv_toggle.setImageDrawable(arrowDrawable);
        rl_toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerLayout.isDrawerOpen()) {
                    drawerLayout.closeDrawer();
                } else {
                    drawerLayout.openDrawer();
                }
            }
        });

        drawerLayout.addDrawerListener(new LeftDrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                float onScreen = (slideOffset - LeftDrawerLayout.DRAWER_ON_SCREEN)
                        * LeftDrawerLayout.CONTENT_OFFSET_FACTOR;
                arrowDrawable.setProgress(onScreen);
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                arrowDrawable.setProgress(1.0f);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                arrowDrawable.setProgress(0.0f);
            }
        });

    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen()) {
            drawerLayout.closeDrawer();
            return;
        }

        super.onBackPressed();
    }

    protected <T extends View> T findView(@IdRes int id) {
        return (T) findViewById(id);
    }

}
