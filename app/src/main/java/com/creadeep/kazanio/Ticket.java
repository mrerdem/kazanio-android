package com.creadeep.kazanio;

import java.io.Serializable;

public class Ticket implements Serializable {
  private String mNumber;
  private String mDate, mFraction, mPrize;
  private int mId, mGameType, mTicketType, mNotify, mIsDrawn;
  private int mTease; // Defines if result or question mark will be shown in tickets activity

  public Ticket() {
  }

  public Ticket(int gameType, int ticketType, String number, String date, String fraction, String prize, int notify, int isDrawn, int tease) {
    this.mGameType = gameType;
    this.mTicketType = ticketType;
    this.mNumber = number;
    this.mDate = date;
    this.mFraction = fraction;
    this.mPrize = prize;
    this.mNotify = notify;
    this.mIsDrawn = isDrawn;
    this.mTease = tease;
  }

  public Ticket(int gameType, int ticketType, String number, String date, int id, String fraction, String prize, int notify, int isDrawn, int tease) {
    this.mId = id;
    this.mGameType = gameType;
    this.mTicketType = ticketType;
    this.mNumber = number;
    this.mDate = date;
    this.mFraction = fraction;
    this.mPrize = prize;
    this.mNotify = notify;
    this.mIsDrawn = isDrawn;
    this.mTease = tease;
  }

  public int getId() {
    return mId;
  }

  public int getGameType() {
    return mGameType;
  }

  public void setGameType(int mType) {
    this.mGameType = mType;
  }

  public int getTicketType() {
    return mTicketType;
  }

  public void setTicketType(int mTicketType) {
    this.mTicketType = mTicketType;
  }

  public Integer getIsDrawn() {
    return mIsDrawn;
  }

  public int getTease() {
    return mTease;
  }

  public void setTease(int mTease) {
    this.mTease = mTease;
  }

  public void setIsDrawn(Integer mIsDrawn) {
    this.mIsDrawn = mIsDrawn;
  }

  public String getPrize() {
    return mPrize;
  }

  public void setPrize(String mPrize) {
    this.mPrize = mPrize;
  }

  public String getDate() {
    return mDate;
  }

  public String getDateFormatted() {
    return mDate.substring(0,2) + "." + mDate.substring(2,4) + "." + mDate.substring(4,8);
  }

  public void setDate(String mDate) {
    this.mDate = mDate;
  }

  public String getFraction() {
    return mFraction;
  }

  public void setFraction(String mFraction) {
    this.mFraction = mFraction;
  }

  public Integer getNotify() {
    return mNotify;
  }

  public void setNotify(Integer mNotify) {
    this.mNotify = mNotify;
  }

  public String getNumber() {
    return mNumber;
  }

  public void setNumber(String mNumber) {
    this.mNumber = mNumber;
  }
}
