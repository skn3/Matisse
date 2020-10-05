package com.zhihu.matisse.listener;

import com.zhihu.matisse.internal.entity.Item;
import com.zhihu.matisse.internal.model.SelectedItemCollection;

/**
 * _   _ _______   ________ _       _____   __
 * | \ | |_   _\ \ / /| ___ \ |     / _ \ \ / /
 * |  \| | | |  \ V / | |_/ / |    / /_\ \ V /
 * | . ` | | |  /   \ |  __/| |    |  _  |\ /
 * | |\  |_| |_/ /^\ \| |   | |____| | | || |
 * \_| \_/\___/\/   \/\_|   \_____/\_| |_/\_/
 * <p>
 * Created by jameskong on 2019-06-03.
 */
public interface SelectionDelegate {
	String getCause(SelectedItemCollection.MaxItemReach reach);
	void onTapItem(Item item, Boolean isDontShow);
}
