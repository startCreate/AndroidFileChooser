package ir.sohreco.androidfilechooser;


import android.app.ProgressDialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatDialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FileChooserDialog extends AppCompatDialogFragment implements ItemHolder.OnItemClickListener, View.OnClickListener {
    private final static String KEY_CHOOSER_TYPE = "chooserType";
    private final static String KEY_CHOOSER_LISTENER = "chooserListener";
    private final static String KEY_TITLE = "title";
    private final static String KEY_FILE_FORMATS = "fileFormats";
    private final static String KEY_INITIAL_DIRECTORY = "initialDirectory";
    private final static String KEY_SELECT_DIRECTORY_BUTTON_TEXT = "selectDirectoryButtonText";
    private final static String KEY_SELECT_DIRECTORY_BUTTON_TEXT_SIZE = "selectDirectoryButtonTextSize";
    private final static String KEY_SELECT_DIRECTORY_BUTTON_TEXT_COLOR_ID = "selectDirectoryButtonTextColorId";
    private final static String KEY_SELECT_DIRECTORY_BUTTON_BACKGROUND_ID = "selectDirectoryButtonBackgroundId";
    private final static String KEY_FILE_ICON_ID = "fileIconId";
    private final static String KEY_DIRECTORY_ICON_ID = "directoryIconId";
    private final static String KEY_PREVIOUS_DIRECTORY_BUTTON_ICON_ID = "previousDirectoryButtonIconId";
    private Button btnPrevDirectory, btnSelectDirectory;
    private RecyclerView rvItems;
    private TextView tvCurrentDirectory;
    private ChooserType chooserType;
    private ChooserListener chooserListener;
    private ChooserPathOpenListener chooserPathOpenListener;
    private ItemsAdapter itemsAdapter;
    private String[] fileFormats;
    private String currentDirectoryPath, title, initialDirectory, selectDirectoryButtonText;
    @DrawableRes
    private int directoryIconId, fileIconId, previousDirectoryButtonIconId, selectDirectoryButtonBackgroundId;
    @ColorRes
    private int selectDirectoryButtonTextColorId;
    private float selectDirectoryButtonTextSize;
    private ProgressDialog mProgressDialog;

    public void showLoadingDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.show();
            return;
        }

        mProgressDialog = ProgressDialog.show(getActivity(), null, getString(R.string.please_wait), true, false);
    }

    public void hideLoadingDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getGivenArguments();
        if (title == null) {
            // Remove dialog's title
            // Since setting the style after the fragment is created doesn't have any effect
            // so we have to decide about dialog's title here.
            setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chooser, container, false);
        findViews(view);
        setListeners();
        getDialog().setTitle(title);

        if (chooserType == ChooserType.DIRECTORY_CHOOSER) {
            btnSelectDirectory.setVisibility(View.VISIBLE);
            btnSelectDirectory.setText(selectDirectoryButtonText);
            if (selectDirectoryButtonBackgroundId != 0)
                btnSelectDirectory.setBackgroundResource(selectDirectoryButtonBackgroundId);
            if (selectDirectoryButtonTextColorId != 0)
                btnSelectDirectory.setTextColor(getResources().getColor(selectDirectoryButtonTextColorId));
            if (selectDirectoryButtonTextSize > 0)
                btnSelectDirectory.setTextSize(selectDirectoryButtonTextSize);
        }
        btnPrevDirectory.setBackgroundResource(previousDirectoryButtonIconId);

        itemsAdapter = new ItemsAdapter(this);
        rvItems.setLayoutManager(new LinearLayoutManager(getActivity()));
        rvItems.setAdapter(itemsAdapter);

        loadItems(initialDirectory != null ? initialDirectory : Environment.getExternalStorageDirectory().getPath());

        return view;
    }

    @Override
    public void onItemClick(Item item) {
        if (item.isDirectory()) {
            loadItems(item.getPath());
        } else {
            chooserListener.onSelect(item.getPath());
            dismiss();
        }
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.previous_dir_imagebutton) {
            File parent = new File(currentDirectoryPath).getParentFile();
            if (parent != null) {
                loadItems(parent.getPath());
            }
        } else if (id == R.id.select_dir_button) {
            chooserListener.onSelect(currentDirectoryPath);
            dismiss();
        }
    }

    private void loadItems(final String path) {
        showLoadingDialog();
        chooserPathOpenListener.startLoading();
        currentDirectoryPath = path;
        String currentDir = path.substring(path.lastIndexOf(File.separator) + 1);
        tvCurrentDirectory.setText(currentDir);
        final List<Item> items = new ArrayList<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        final Future<List<Item>> submit = executor.submit(new Callable<List<Item>>() {
            @Override public List<Item> call() throws Exception {
                File[] files = new File(path).listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        if (file.canRead()) {
                            if (chooserType == ChooserType.FILE_CHOOSER && file.isFile()) {
                                if (fileFormats != null && fileFormats.length > 0) {
                                    for (String fileFormat : fileFormats) {
                                        if (file.getName().endsWith(fileFormat)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                }
                                return true;
                            }
                            return file.isDirectory();
                        }
                        return false;
                    }
                });

                if (files != null) {
                    for (File f : files) {
                        int drawableId = f.isFile() ? fileIconId : directoryIconId;
                        Drawable drawable = ContextCompat.getDrawable(getActivity().getApplicationContext(), drawableId);
                        items.add(new Item(f.getPath(), drawable));
                    }
                    Collections.sort(items);
                }
                return items;
            }
        });
        try {
            itemsAdapter.setItems(submit.get());
            hideLoadingDialog();
            chooserPathOpenListener.finishLoading();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void getGivenArguments() {
        Bundle args = getArguments();
        chooserType = (ChooserType) args.getSerializable(KEY_CHOOSER_TYPE);
        title = args.getString(KEY_TITLE);
        fileFormats = args.getStringArray(KEY_FILE_FORMATS);
        initialDirectory = args.getString(KEY_INITIAL_DIRECTORY);
        selectDirectoryButtonText = args.getString(KEY_SELECT_DIRECTORY_BUTTON_TEXT);
        selectDirectoryButtonTextSize = args.getFloat(KEY_SELECT_DIRECTORY_BUTTON_TEXT_SIZE);
        selectDirectoryButtonTextColorId = args.getInt(KEY_SELECT_DIRECTORY_BUTTON_TEXT_COLOR_ID);
        selectDirectoryButtonBackgroundId = args.getInt(KEY_SELECT_DIRECTORY_BUTTON_BACKGROUND_ID);
        fileIconId = args.getInt(KEY_FILE_ICON_ID);
        directoryIconId = args.getInt(KEY_DIRECTORY_ICON_ID);
        previousDirectoryButtonIconId = args.getInt(KEY_PREVIOUS_DIRECTORY_BUTTON_ICON_ID);
    }

    private void setListeners() {
        btnPrevDirectory.setOnClickListener(this);
        btnSelectDirectory.setOnClickListener(this);
    }

    private void findViews(View v) {
        rvItems = (RecyclerView) v.findViewById(R.id.items_recyclerview);
        btnPrevDirectory = (Button) v.findViewById(R.id.previous_dir_imagebutton);
        btnSelectDirectory = (Button) v.findViewById(R.id.select_dir_button);
        tvCurrentDirectory = (TextView) v.findViewById(R.id.current_dir_textview);
    }

    public enum ChooserType {
        FILE_CHOOSER,
        DIRECTORY_CHOOSER
    }

    public interface ChooserListener {
        /**
         * This method gets called when user selects a file or a directory depending on the chooser type.
         *
         * @param path path of the selected file or directory.
         */
        void onSelect(String path);
    }

    /*Interface for indicating loading process paths from opened directory*/
    public interface ChooserPathOpenListener {
        void startLoading();

        void finishLoading();
    }

    public static class Builder {
        // Required parameters
        private ChooserType chooserType;
        private ChooserListener chooserListener;
        private ChooserPathOpenListener chooserPathOpenListener;

        // Optional parameters
        private String[] fileFormats;
        private String title, selectDirectoryButtonText, initialDirectory;
        @DrawableRes
        private int fileIconId = R.drawable.ic_file;
        @DrawableRes
        private int directoryIconId = R.drawable.ic_directory;
        @DrawableRes
        private int previousDirectoryButtonIcon = R.drawable.ic_prev_dir;
        @DrawableRes
        private int selectDirectoryButtonBackgroundId;
        @ColorRes
        private int selectDirectoryButtonTextColorId;
        private float selectDirectoryButtonTextSize;

        /**
         * Creates a builder for a FileChooser fragment.
         *
         * @param chooserType You can choose to create either a FileChooser or a DirectoryChooser
         */
        public Builder(ChooserType chooserType, ChooserListener chooserListener, ChooserPathOpenListener chooserPathOpenListener) {
            this.chooserType = chooserType;
            this.chooserListener = chooserListener;
            this.chooserPathOpenListener = chooserPathOpenListener;
        }

        /**
         * Set the title of this FileChooserDialog.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        /**
         * Set file formats which are going to be shown by this FileChooserDialog.
         * All types of files will be shown if you don't set it.
         *
         * @param fileFormats A string array of file formats
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFileFormats(String[] fileFormats) {
            this.fileFormats = fileFormats;
            return this;
        }

        /**
         * Set the initial directory of this FileChooserDialog.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         * @throws IllegalArgumentException if:<br>
         *                                  initialDirectory does not exist.<br>
         *                                  initialDirectory is not a directory.<br>
         *                                  initialDirectory is not accessible due to access restrictions.
         */
        public Builder setInitialDirectory(File initialDirectory) {
            if (initialDirectory == null)
                throw new NullPointerException("initialDirectory can't be null.");

            if (!initialDirectory.exists())
                throw new IllegalArgumentException(initialDirectory.getPath() + " Does not exist.");

            if (!initialDirectory.isDirectory())
                throw new IllegalArgumentException(initialDirectory.getPath() + " Is not a directory.");

            if (!initialDirectory.canRead())
                throw new IllegalArgumentException("Can't access " + initialDirectory.getPath());

            this.initialDirectory = initialDirectory.getPath();
            return this;
        }

        /**
         * Set select directory button's text. You will see this button when chooser type is DIRECTORY_CHOOSER.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSelectDirectoryButtonText(String text) {
            this.selectDirectoryButtonText = text;
            return this;
        }

        /**
         * Set select directory button's text size.
         *
         * @param textSize must be based on scaled pixel(SP) unit.
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSelectDirectoryButtonTextSize(float textSize) {
            selectDirectoryButtonTextSize = textSize;
            return this;
        }

        /**
         * Set select directory button's text color. You will see this button when chooser type is DIRECTORY_CHOOSER.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSelectDirectoryButtonTextColor(@ColorRes int colorId) {
            selectDirectoryButtonTextColorId = colorId;
            return this;
        }

        /**
         * Set select directory button's background. You will see this button when chooser type is DIRECTORY_CHOOSER.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setSelectDirectoryButtonBackground(@DrawableRes int backgroundId) {
            selectDirectoryButtonBackgroundId = backgroundId;
            return this;
        }

        /**
         * Set the icon for files in this FileChooserDialog's list
         * Default icon will be used if you don't set it.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setFileIcon(@DrawableRes int iconId) {
            fileIconId = iconId;
            return this;
        }

        /**
         * Set the icon for directories in this FileChooserDialog's list.
         * Default icon will be used if you don't set it.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setDirectoryIcon(@DrawableRes int iconId) {
            directoryIconId = iconId;
            return this;
        }

        /**
         * Set the icon for the button that is going to be used to go to the parent of the current directory.
         * Default icon will be used if you don't set it.
         *
         * @return This Builder object to allow for chaining of calls to set methods
         */
        public Builder setPreviousDirectoryButtonIcon(@DrawableRes int iconId) {
            previousDirectoryButtonIcon = iconId;
            return this;
        }

        /**
         * Returns an instance of FileChooserDialog with the given configurations.
         *
         * @throws ExternalStorageNotAvailableException If there is no external storage available on user's device
         */
        public FileChooserDialog build() throws ExternalStorageNotAvailableException {
            String externalStorageState = Environment.getExternalStorageState();
            boolean externalStorageAvailable = externalStorageState.equals(Environment.MEDIA_MOUNTED)
                    || externalStorageState.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
            if (!externalStorageAvailable) {
                throw new ExternalStorageNotAvailableException();
            }

            FileChooserDialog fragment = new FileChooserDialog();

            Bundle args = new Bundle();
            args.putSerializable(KEY_CHOOSER_TYPE, chooserType);
            fragment.chooserListener = chooserListener;
            fragment.chooserPathOpenListener = chooserPathOpenListener;
            args.putString(KEY_TITLE, title);
            args.putStringArray(KEY_FILE_FORMATS, fileFormats);
            args.putString(KEY_INITIAL_DIRECTORY, initialDirectory);
            args.putString(KEY_SELECT_DIRECTORY_BUTTON_TEXT, selectDirectoryButtonText);
            args.putFloat(KEY_SELECT_DIRECTORY_BUTTON_TEXT_SIZE, selectDirectoryButtonTextSize);
            args.putInt(KEY_SELECT_DIRECTORY_BUTTON_TEXT_COLOR_ID, selectDirectoryButtonTextColorId);
            args.putInt(KEY_SELECT_DIRECTORY_BUTTON_BACKGROUND_ID, selectDirectoryButtonBackgroundId);
            args.putInt(KEY_FILE_ICON_ID, fileIconId);
            args.putInt(KEY_DIRECTORY_ICON_ID, directoryIconId);
            args.putInt(KEY_PREVIOUS_DIRECTORY_BUTTON_ICON_ID, previousDirectoryButtonIcon);

            fragment.setArguments(args);

            return fragment;
        }
    }
}
