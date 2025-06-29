package com.gaaiuswallet.app.ui.widget.adapter;

import static com.gaaiuswallet.app.service.AssetDefinitionService.ASSET_SUMMARY_VIEW_NAME;

import android.content.Context;
import android.util.Pair;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.recyclerview.widget.SortedList;

import com.gaaiuswallet.app.R;
import com.gaaiuswallet.app.entity.TicketRangeElement;
import com.gaaiuswallet.app.entity.nftassets.NFTAsset;
import com.gaaiuswallet.app.entity.tokendata.TokenGroup;
import com.gaaiuswallet.app.entity.tokens.ERC721Token;
import com.gaaiuswallet.app.entity.tokens.Token;
import com.gaaiuswallet.app.service.AssetDefinitionService;
import com.gaaiuswallet.app.service.OpenSeaService;
import com.gaaiuswallet.app.ui.widget.NonFungibleAdapterInterface;
import com.gaaiuswallet.app.ui.widget.TokensAdapterCallback;
import com.gaaiuswallet.app.ui.widget.entity.AssetInstanceSortedItem;
import com.gaaiuswallet.app.ui.widget.entity.NFTSortedItem;
import com.gaaiuswallet.app.ui.widget.entity.QuantitySelectorSortedItem;
import com.gaaiuswallet.app.ui.widget.entity.SortedItem;
import com.gaaiuswallet.app.ui.widget.entity.TokenIdSortedItem;
import com.gaaiuswallet.app.ui.widget.entity.TokenPosition;
import com.gaaiuswallet.app.ui.widget.holder.AssetInstanceScriptHolder;
import com.gaaiuswallet.app.ui.widget.holder.BinderViewHolder;
import com.gaaiuswallet.app.ui.widget.holder.NFTAssetHolder;
import com.gaaiuswallet.app.ui.widget.holder.QuantitySelectorHolder;
import com.gaaiuswallet.app.ui.widget.holder.TextHolder;
import com.gaaiuswallet.app.ui.widget.holder.TicketHolder;
import com.gaaiuswallet.app.ui.widget.holder.TokenDescriptionHolder;
import com.gaaiuswallet.app.ui.widget.holder.TotalBalanceHolder;
import com.gaaiuswallet.token.entity.TicketRange;
import com.gaaiuswallet.token.entity.ViewType;
import com.bumptech.glide.Glide;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by James on 9/02/2018.
 */

public class NonFungibleTokenAdapter extends TokensAdapter implements NonFungibleAdapterInterface
{
    TicketRange currentRange = null;
    final Token token;
    protected final OpenSeaService openseaService;
    private final ViewType clickThrough;
    protected int assetCount;
    private boolean isGrid;

    public NonFungibleTokenAdapter(TokensAdapterCallback tokenClickListener, Token t, AssetDefinitionService service,
                                   OpenSeaService opensea)
    {
        super(tokenClickListener, service);
        assetCount = 0;
        token = t;
        clickThrough = ViewType.ITEM_VIEW;
        openseaService = opensea;
        setToken(t);
    }

    public NonFungibleTokenAdapter(TokensAdapterCallback tokenClickListener, Token t, AssetDefinitionService service,
                                   OpenSeaService opensea, boolean isGrid)
    {
        super(tokenClickListener, service);
        assetCount = 0;
        token = t;
        clickThrough = ViewType.ITEM_VIEW;
        openseaService = opensea;
        this.isGrid = isGrid;
        setToken(t);
    }

    public NonFungibleTokenAdapter(TokensAdapterCallback tokenClickListener, Token t, List<BigInteger> tokenSelection,
                                   AssetDefinitionService service)
    {
        super(tokenClickListener, service);
        assetCount = 0;
        token = t;
        clickThrough = ViewType.VIEW;
        openseaService = null;
        setTokenRange(token, tokenSelection);
    }

    public NonFungibleTokenAdapter(TokensAdapterCallback tokenClickListener, Token t, ArrayList<Pair<BigInteger, NFTAsset>> assetSelection,
                                   AssetDefinitionService service)
    {
        super(tokenClickListener, service);
        assetCount = 0;
        token = t;
        clickThrough = ViewType.VIEW;
        openseaService = null;
        setAssetSelection(token, assetSelection);
    }

    private void setAssetSelection(Token token, List<Pair<BigInteger, NFTAsset>> selection)
    {
        setAssetRange(token, selection);
    }

    @NotNull
    @Override
    public BinderViewHolder<?> onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        BinderViewHolder<?> holder = null;
        switch (viewType)
        {
            case TicketHolder.VIEW_TYPE: //Ticket holder now deprecated //TODO: remove
                holder = new TicketHolder(R.layout.item_ticket, parent, token, assetService);
                holder.setOnTokenClickListener(tokensAdapterCallback);
                break;
            case TotalBalanceHolder.VIEW_TYPE:
                holder = new TotalBalanceHolder(R.layout.item_total_balance, parent);
                break;
            case TokenDescriptionHolder.VIEW_TYPE:
                holder = new TokenDescriptionHolder(R.layout.item_token_description, parent, token, assetService, assetCount);
                break;
            case AssetInstanceScriptHolder.VIEW_TYPE:
                holder = new AssetInstanceScriptHolder(R.layout.item_ticket, parent, token, assetService, clickThrough);
                holder.setOnTokenClickListener(tokensAdapterCallback);
                break;
            case NFTAssetHolder.VIEW_TYPE:
                holder = new NFTAssetHolder(parent);
                break;
            case QuantitySelectorHolder.VIEW_TYPE:
                holder = new QuantitySelectorHolder(R.layout.item_quantity_selector, parent, assetCount, assetService);
                break;
            default:
                holder = new TextHolder(R.layout.item_standard_header, parent);
                break;
        }

        return holder;
    }

    public int getTicketRangeCount()
    {
        int count = 0;
        if (currentRange != null)
        {
            count = currentRange.tokenIds.size();
        }
        return count;
    }

    public void addQuantitySelector()
    {
        items.add(new QuantitySelectorSortedItem(token));
    }

    private void setTokenRange(Token t, List<BigInteger> tokenIds)
    {
        items.beginBatchedUpdates();
        items.clear();
        assetCount = tokenIds.size();
        int holderType = getHolderType();

        //TokenScript view for ERC721 overrides OpenSea display
        if (assetService.hasTokenView(t, ASSET_SUMMARY_VIEW_NAME)) holderType = AssetInstanceScriptHolder.VIEW_TYPE;

        List<TicketRangeElement> sortedList = generateSortedList(assetService, token, tokenIds); //generate sorted list
        addSortedItems(sortedList, t, holderType); //insert sorted items into view

        items.endBatchedUpdates();
    }

    public void setToken(Token t)
    {
        items.beginBatchedUpdates();
        items.clear();
        assetCount = t.getTokenCount();
        int holderType = getHolderType();

        //TokenScript view for ERC721 overrides OpenSea display
        if (assetService.hasTokenView(t, ASSET_SUMMARY_VIEW_NAME)) holderType = AssetInstanceScriptHolder.VIEW_TYPE;

        addRanges(t, holderType);
        items.endBatchedUpdates();
    }

    private void setAssetRange(Token t, List<Pair<BigInteger, NFTAsset>> selection)
    {
        items.beginBatchedUpdates();
        items.clear();
        assetCount = selection.size();

        for (int i = 0; i < selection.size(); i++)
        {
            items.add(new NFTSortedItem(selection.get(i), i + 1));
        }

        items.endBatchedUpdates();
    }

    private void addRanges(Token t, int holderType)
    {
        currentRange = null;
        List<TicketRangeElement> sortedList = generateSortedList(assetService, t, t.getArrayBalance());
        addSortedItems(sortedList, t, holderType);
    }

    protected List<TicketRangeElement> generateSortedList(AssetDefinitionService assetService, Token token, List<BigInteger> idList)
    {
        List<TicketRangeElement> sortedList = new ArrayList<>();
        for (BigInteger v : idList)
        {
            if (v.compareTo(BigInteger.ZERO) == 0) continue;
            TicketRangeElement e = new TicketRangeElement(assetService, token, v);
            sortedList.add(e);
        }
        TicketRangeElement.sortElements(sortedList);
        return sortedList;
    }

    @SuppressWarnings("unchecked")
    protected <T> T generateType(TicketRange range, int weight, int id)
    {
        TokenPosition tp = new TokenPosition(TokenGroup.NFT, 1, weight);
        T item;
        switch (id)
        {
            case AssetInstanceScriptHolder.VIEW_TYPE:
                item = (T) new AssetInstanceSortedItem(range, tp);
                break;
            case TicketHolder.VIEW_TYPE:
            default:
                item = (T) new TokenIdSortedItem(range, tp);
                break;
        }

        return item;
    }

    protected <T> SortedList<T> addSortedItems(List<TicketRangeElement> sortedList, Token t, int id)
    {
        long currentTime = 0;
        for (int i = 0; i < sortedList.size(); i++)
        {
            TicketRangeElement e = sortedList.get(i);
            if (currentRange != null && t.groupWithToken(currentRange, e, currentTime))
            {
                currentRange.tokenIds.add(e.id);
            }
            else
            {
                currentRange = new TicketRange(e.id, t.getAddress());
                final T item = generateType(currentRange, 10 + i, id);
                items.add((SortedItem) item);
                currentTime = e.time;
            }
        }

        return null;
    }

    private Single<Boolean> clearCache(Context ctx)
    {
        return Single.fromCallable(() -> {
            Glide.get(ctx).clearDiskCache();
            return true;
        });
    }

    //TODO: Find out how to calculate the storage hash for each image and reproduce that, deleting only the right image.
    //TODO: Possibly the best way is not to use glide, revert back to caching images as in the original implementation.
    public void reloadAssets(Context ctx)
    {
        if (token instanceof ERC721Token)
        {
            clearCache(ctx)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::cleared, error -> Timber.d("Cache clean: " + error.getMessage()))
                    .isDisposed();
        }
    }

    private void cleared(Boolean aBoolean)
    {
        this.notifyDataSetChanged();
    }

    @Override
    public void setRadioButtons(boolean expose)
    {
        boolean requiresFullRedraw = false;
        //uncheck all ranges, note that the selected range will be checked after the refresh
        for (int i = 0; i < items.size(); i++)
        {
            SortedItem si = items.get(i);
            if (si.isRadioExposed() != expose) requiresFullRedraw = true;
            if (si.view != null)
            {
                AppCompatRadioButton button = si.view.itemView.findViewById(R.id.radioBox);
                if (button != null && (button.isChecked() || si.isItemChecked())) button.setChecked(false);
            }
            si.setIsChecked(false);
            si.setExposeRadio(expose);
        }

        if (requiresFullRedraw)
        {
            notifyDataSetChanged();
        }
    }

    @Override
    public List<BigInteger> getSelectedTokenIds(List<BigInteger> selection)
    {
        List<BigInteger> tokenIds = new ArrayList<>(selection);
        for (int i = 0; i < items.size(); i++)
        {
            SortedItem si = items.get(i);
            if (si.isItemChecked())
            {
                List<BigInteger> rangeIds = si.getTokenIds();
                for (BigInteger tokenId : rangeIds) if (!tokenIds.contains(tokenId)) tokenIds.add(tokenId);
            }
        }

        return tokenIds;
    }

    @Override
    public int getSelectedGroups()
    {
        int selected = 0;
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).isItemChecked()) selected++;
        }

        return selected;
    }

    public int getSelectedQuantity()
    {
        for (int i = 0; i < items.size(); i++)
        {
            SortedItem si = items.get(i);
            if (si.view.getItemViewType() == QuantitySelectorHolder.VIEW_TYPE)
            {
                return ((QuantitySelectorHolder) si.view).getCurrentQuantity();
            }
        }
        return 0;
    }

    public TicketRange getSelectedRange(List<BigInteger> selection)
    {
        int quantity = getSelectedQuantity();
        if (quantity > selection.size()) quantity = selection.size();
        List<BigInteger> subSelection = new ArrayList<>();

        for (int i = 0; i < quantity; i++)
        {
            subSelection.add(selection.get(i));
        }

        return new TicketRange(subSelection, token.getAddress(), false);
    }

    private int getHolderType()
    {
        return AssetInstanceScriptHolder.VIEW_TYPE;
    }
}
